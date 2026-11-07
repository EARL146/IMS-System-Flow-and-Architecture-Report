package com.ims.api.customer;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.catalog.Product;
import com.ims.api.common.ApiException;
import com.ims.api.common.PageResponse;
import com.ims.api.config.CheckoutProperties;
import com.ims.api.inventory.InventoryService;
import com.ims.api.warehouse.Warehouse;
import com.ims.api.warehouse.WarehouseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class CustomerOrderService {
    private static final int MAX_ORDER_LINES = 200;
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(4);

    private final CustomerOrderRepository orders;
    private final CustomerOrderStatusHistoryRepository statusHistory;
    private final CustomerAddressRepository addresses;
    private final CartItemRepository cartItems;
    private final CheckoutIdempotencyKeyRepository checkoutKeys;
    private final AppUserRepository users;
    private final WarehouseRepository warehouses;
    private final InventoryService inventory;
    private final CheckoutProperties checkoutProperties;
    private final Clock clock;

    public CustomerOrderService(
            CustomerOrderRepository orders,
            CustomerOrderStatusHistoryRepository statusHistory,
            CustomerAddressRepository addresses,
            CartItemRepository cartItems,
            CheckoutIdempotencyKeyRepository checkoutKeys,
            AppUserRepository users,
            WarehouseRepository warehouses,
            InventoryService inventory,
            CheckoutProperties checkoutProperties,
            Clock clock
    ) {
        this.orders = orders;
        this.statusHistory = statusHistory;
        this.addresses = addresses;
        this.cartItems = cartItems;
        this.checkoutKeys = checkoutKeys;
        this.users = users;
        this.warehouses = warehouses;
        this.inventory = inventory;
        this.checkoutProperties = checkoutProperties;
        this.clock = clock;
    }

    @Transactional
    public CustomerDtos.OrderResponse checkout(String email, CustomerDtos.CheckoutRequest request) {
        AppUser customer = lockUser(email);
        String idempotencyKey = request.idempotencyKey().toString();
        String requestHash = requestHash(request);

        if (checkoutKeys.reserveIfAbsent(customer.getId(), idempotencyKey, requestHash) == 0) {
            CheckoutIdempotencyKey existing = checkoutKeys.findForUpdate(customer.getId(), idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Checkout idempotency reservation disappeared"));
            if (!existing.getRequestHash().equals(requestHash)) {
                throw ApiException.conflict("Checkout idempotency key was already used for a different request");
            }
            if (existing.getCustomerOrder() == null) {
                throw new IllegalStateException("Checkout idempotency reservation has no order");
            }
            return CustomerDtos.OrderResponse.from(loadOwnedOrder(existing.getCustomerOrder().getId(), customer.getId()));
        }

        Warehouse warehouse = warehouses.findById(request.warehouseId())
                .filter(Warehouse::isActive)
                .orElseThrow(() -> ApiException.notFound("Active fulfillment location not found"));
        CustomerAddress address = addresses.findByIdAndUser_Id(request.addressId(), customer.getId())
                .orElseThrow(() -> ApiException.notFound("Delivery address not found"));
        var cart = cartItems.findAllForUpdateByUser(customer.getId());
        if (cart.isEmpty()) throw ApiException.conflict("Your cart is empty");
        if (cart.size() > MAX_ORDER_LINES) throw ApiException.conflict("An order can contain at most 200 products");

        validateCheckoutConfiguration();
        BigDecimal subtotal = ZERO_MONEY;
        ArrayList<CustomerOrderItem> orderLines = new ArrayList<>();
        short lineNumber = 1;
        for (CartItem cartItem : cart) {
            Product product = cartItem.getProduct();
            if (!product.isActive()) {
                throw ApiException.conflict("A product in your cart is no longer available: " + product.getSku());
            }
            BigDecimal quantity = cartItem.getQuantity();
            if (quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 3) {
                throw ApiException.conflict("A cart quantity is invalid");
            }
            BigDecimal unitPrice = product.getSellingPrice().setScale(4, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(quantity).setScale(4, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            orderLines.add(new CustomerOrderItem(lineNumber++, product, quantity, unitPrice, lineTotal));
        }
        subtotal = subtotal.setScale(4, RoundingMode.HALF_UP);
        BigDecimal taxAmount = subtotal.multiply(checkoutProperties.taxRate()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal shippingAmount = checkoutProperties.shippingAmount().setScale(4, RoundingMode.HALF_UP);
        BigDecimal discountAmount = ZERO_MONEY;
        BigDecimal total = subtotal.add(taxAmount).add(shippingAmount).subtract(discountAmount)
                .setScale(4, RoundingMode.HALF_UP);
        PaymentStatus paymentStatus = request.paymentMethod() == PaymentMethod.MANUAL_TRANSFER
                ? PaymentStatus.AWAITING_VERIFICATION
                : PaymentStatus.UNPAID;

        CustomerOrder order = new CustomerOrder(nextOrderNumber(), customer, warehouse,
                request.paymentMethod(), paymentStatus, checkoutProperties.currencyCode().toUpperCase(Locale.ROOT),
                subtotal, taxAmount, shippingAmount, discountAmount, total, address, clean(request.notes()));
        orderLines.forEach(order::addItem);
        CustomerOrder saved = orders.saveAndFlush(order);
        statusHistory.save(new CustomerOrderStatusHistory(saved, null, CustomerOrderStatus.PENDING,
                customer, "Order placed"));

        for (CustomerOrderItem item : saved.getItems()) {
            inventory.reserveCustomerOrderStock(
                    item.getProduct().getId(), warehouse.getId(), item.getQuantity(), email,
                    movementKey("reserve", saved.getId(), item.getId()), saved.getOrderNumber());
        }

        cartItems.deleteByUser_Id(customer.getId());
        CheckoutIdempotencyKey reservation = checkoutKeys.findForUpdate(customer.getId(), idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Saved checkout idempotency reservation could not be reloaded"));
        reservation.setCustomerOrder(saved);
        checkoutKeys.saveAndFlush(reservation);
        return CustomerDtos.OrderResponse.from(loadOwnedOrder(saved.getId(), customer.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerDtos.OrderResponse> list(String email, int page, int size) {
        AppUser customer = requireUser(email);
        return PageResponse.from(orders.findByCustomer_IdOrderByCreatedAtDesc(customer.getId(), PageRequest.of(page, size))
                .map(CustomerDtos.OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public CustomerDtos.OrderResponse get(long orderId, String email) {
        AppUser customer = requireUser(email);
        return CustomerDtos.OrderResponse.from(loadOwnedOrder(orderId, customer.getId()));
    }

    @Transactional(readOnly = true)
    public java.util.List<CustomerDtos.OrderStatusHistoryResponse> history(long orderId, String email) {
        AppUser customer = requireUser(email);
        loadOwnedOrder(orderId, customer.getId());
        return statusHistory.findAllByCustomerOrder_IdOrderByOccurredAtAscIdAsc(orderId).stream()
                .map(CustomerDtos.OrderStatusHistoryResponse::from).toList();
    }

    @Transactional
    public CustomerDtos.OrderResponse cancel(long orderId, String email) {
        AppUser customer = requireUser(email);
        CustomerOrder order = orders.findDetailedForUpdateByIdAndCustomer(orderId, customer.getId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        CustomerOrderStatus previousStatus = order.getStatus();
        try {
            order.cancel(clock.instant());
        } catch (IllegalStateException exception) {
            throw ApiException.conflict(exception.getMessage());
        }
        statusHistory.save(new CustomerOrderStatusHistory(order, previousStatus, CustomerOrderStatus.CANCELLED,
                customer, "Cancelled by customer"));

        for (CustomerOrderItem item : order.getItems()) {
            inventory.releaseCustomerOrderStock(
                    item.getProduct().getId(), order.getWarehouse().getId(), item.getQuantity(), email,
                    movementKey("release", order.getId(), item.getId()), order.getOrderNumber());
        }
        orders.saveAndFlush(order);
        return CustomerDtos.OrderResponse.from(order);
    }

    private CustomerOrder loadOwnedOrder(long orderId, long customerId) {
        return orders.findDetailedByIdAndCustomer_Id(orderId, customerId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    private AppUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(ApiException::unauthorized);
    }

    private AppUser lockUser(String email) {
        return users.findByEmailIgnoreCaseForUpdate(email).orElseThrow(ApiException::unauthorized);
    }

    private void validateCheckoutConfiguration() {
        BigDecimal taxRate = checkoutProperties.taxRate();
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("Checkout tax rate must be between zero and one");
        }
        if (checkoutProperties.shippingAmount().signum() < 0) {
            throw new IllegalStateException("Checkout shipping amount cannot be negative");
        }
        if (!checkoutProperties.currencyCode().matches("[A-Za-z]{3}")) {
            throw new IllegalStateException("Checkout currency code must contain three letters");
        }
    }

    private String nextOrderNumber() {
        return "SO-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String movementKey(String operation, long orderId, long itemId) {
        return UUID.nameUUIDFromBytes(("ims:sales:" + operation + ":" + orderId + ":" + itemId)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String requestHash(CustomerDtos.CheckoutRequest request) {
        String canonical = request.addressId() + "|" + request.warehouseId() + "|"
                + request.paymentMethod().name() + "|" + (clean(request.notes()) == null ? "" : clean(request.notes()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable", exception);
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
