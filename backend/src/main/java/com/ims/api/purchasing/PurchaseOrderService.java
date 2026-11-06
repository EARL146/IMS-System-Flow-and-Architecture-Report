package com.ims.api.purchasing;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.catalog.Product;
import com.ims.api.catalog.ProductRepository;
import com.ims.api.common.ApiException;
import com.ims.api.common.PageResponse;
import com.ims.api.inventory.InventoryService;
import com.ims.api.warehouse.Warehouse;
import com.ims.api.warehouse.WarehouseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderService {
    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final AppUserRepository users;
    private final GoodsReceiptRepository receipts;
    private final PurchaseReceiptIdempotencyRepository receiptKeys;
    private final InventoryService inventory;
    private final Clock clock;

    public PurchaseOrderService(
            PurchaseOrderRepository orders,
            SupplierRepository suppliers,
            ProductRepository products,
            WarehouseRepository warehouses,
            AppUserRepository users,
            GoodsReceiptRepository receipts,
            PurchaseReceiptIdempotencyRepository receiptKeys,
            InventoryService inventory,
            Clock clock
    ) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.products = products;
        this.warehouses = warehouses;
        this.users = users;
        this.receipts = receipts;
        this.receiptKeys = receiptKeys;
        this.inventory = inventory;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderDtos.Response> list(int page, int size) {
        return PageResponse.from(orders.findRecent(PageRequest.of(page, size))
                .map(PurchaseOrderDtos.Response::from));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderDtos.Response get(long id) {
        return PurchaseOrderDtos.Response.from(loadDetailed(id));
    }

    @Transactional
    public PurchaseOrderDtos.Response create(PurchaseOrderDtos.CreateRequest request, String actorEmail) {
        Supplier supplier = suppliers.findById(request.supplierId())
                .filter(Supplier::isActive)
                .orElseThrow(() -> ApiException.notFound("Active supplier not found"));
        Warehouse warehouse = warehouses.findById(request.warehouseId())
                .filter(Warehouse::isActive)
                .orElseThrow(() -> ApiException.notFound("Active warehouse not found"));
        AppUser actor = requireUser(actorEmail);

        Set<Long> productIds = new HashSet<>();
        PurchaseOrder order = new PurchaseOrder(
                nextOrderNumber(),
                supplier,
                warehouse,
                request.currencyCode(),
                request.expectedDate(),
                clean(request.notes()),
                actor);

        short lineNumber = 1;
        for (PurchaseOrderDtos.LineRequest line : request.items()) {
            if (!productIds.add(line.productId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A product may appear only once in a purchase order");
            }
            Product product = products.findById(line.productId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> ApiException.notFound("Active product not found: " + line.productId()));
            order.addItem(new PurchaseOrderItem(lineNumber++, product, line.quantity(), line.unitCost()));
        }

        PurchaseOrder saved = orders.saveAndFlush(order);
        return PurchaseOrderDtos.Response.from(loadDetailed(saved.getId()));
    }

    @Transactional
    public PurchaseOrderDtos.Response submit(long id) {
        PurchaseOrder order = loadForUpdate(id);
        transition(order, PurchaseOrder::submit);
        return PurchaseOrderDtos.Response.from(order);
    }

    @Transactional
    public PurchaseOrderDtos.Response approve(long id, String reviewerEmail) {
        PurchaseOrder order = loadForUpdate(id);
        transition(order, value -> value.approve(requireUser(reviewerEmail), clock.instant()));
        return PurchaseOrderDtos.Response.from(order);
    }

    @Transactional
    public PurchaseOrderDtos.Response reject(long id, String reviewerEmail, PurchaseOrderDtos.RejectRequest request) {
        PurchaseOrder order = loadForUpdate(id);
        transition(order, value -> value.reject(requireUser(reviewerEmail), clock.instant(), request.reason().trim()));
        return PurchaseOrderDtos.Response.from(order);
    }

    @Transactional
    public PurchaseOrderDtos.Response markOrdered(long id) {
        PurchaseOrder order = loadForUpdate(id);
        transition(order, value -> value.markOrdered(clock.instant()));
        return PurchaseOrderDtos.Response.from(order);
    }

    @Transactional
    public PurchaseOrderDtos.Response cancel(long id) {
        PurchaseOrder order = loadForUpdate(id);
        transition(order, PurchaseOrder::cancel);
        return PurchaseOrderDtos.Response.from(order);
    }

    @Transactional
    public PurchaseOrderDtos.ReceiptResponse receive(
            long orderId,
            PurchaseOrderDtos.ReceiveRequest request,
            String receiverEmail
    ) {
        String key = request.idempotencyKey().toString();
        String requestHash = receiptRequestHash(orderId, request);

        if (receiptKeys.reserveIfAbsent(key, requestHash) == 0) {
            PurchaseReceiptIdempotencyKey existingKey = receiptKeys.findById(key)
                    .orElseThrow(() -> new IllegalStateException("Receipt idempotency reservation disappeared"));
            if (!existingKey.getRequestHash().equals(requestHash)) {
                throw ApiException.conflict("Receipt idempotency key was already used for a different request");
            }
            return PurchaseOrderDtos.ReceiptResponse.from(receipts.findByIdempotencyKey(key)
                    .orElseThrow(() -> new IllegalStateException("Receipt idempotency reservation has no receipt")));
        }

        PurchaseOrder order = loadForUpdate(orderId);
        if (order.getStatus() != PurchaseOrderStatus.ORDERED
                && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw ApiException.conflict("Goods can be received only for an ordered purchase order");
        }

        AppUser receiver = requireUser(receiverEmail);
        Map<Long, PurchaseOrderItem> orderItems = order.getItems().stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, item -> item));
        Set<Long> seenLines = new HashSet<>();
        ArrayList<Map.Entry<PurchaseOrderItem, BigDecimal>> validatedLines = new ArrayList<>();
        for (PurchaseOrderDtos.ReceiveLineRequest line : request.items()) {
            if (!seenLines.add(line.purchaseOrderItemId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A purchase order line may appear only once per receipt");
            }
            PurchaseOrderItem orderItem = orderItems.get(line.purchaseOrderItemId());
            if (orderItem == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Receipt line does not belong to this purchase order");
            }
            if (line.quantity().signum() <= 0
                    || line.quantity().stripTrailingZeros().scale() > 3
                    || line.quantity().compareTo(orderItem.getOutstandingQuantity()) > 0) {
                throw ApiException.conflict("Received quantity must be positive and cannot exceed the outstanding quantity");
            }
            validatedLines.add(Map.entry(orderItem, line.quantity()));
        }

        GoodsReceipt receipt = new GoodsReceipt(order, key, requestHash, clean(request.deliveryReference()),
                receiver, clean(request.notes()));
        receipts.saveAndFlush(receipt);

        for (Map.Entry<PurchaseOrderItem, BigDecimal> line : validatedLines) {
            PurchaseOrderItem orderItem = line.getKey();
            BigDecimal quantity = line.getValue();
            orderItem.receive(quantity);
            receipt.addItem(new GoodsReceiptItem(orderItem, quantity));

            String movementKey = UUID.nameUUIDFromBytes(
                    ("ims:purchase-receipt:" + receipt.getId() + ":" + orderItem.getId())
                            .getBytes(StandardCharsets.UTF_8))
                    .toString();
            inventory.receivePurchaseOrderStock(
                    orderItem.getProduct().getId(),
                    order.getWarehouse().getId(),
                    quantity,
                    receiverEmail,
                    movementKey,
                    order.getOrderNumber(),
                    receipt.getId().toString());
        }

        order.refreshReceiptStatus();
        orders.saveAndFlush(order);
        receipts.saveAndFlush(receipt);
        return PurchaseOrderDtos.ReceiptResponse.from(receipts.findByIdempotencyKey(key)
                .orElseThrow(() -> new IllegalStateException("Saved goods receipt could not be reloaded")));
    }

    private PurchaseOrder loadDetailed(long id) {
        return orders.findDetailedById(id)
                .orElseThrow(() -> ApiException.notFound("Purchase order not found"));
    }

    private PurchaseOrder loadForUpdate(long id) {
        return orders.findDetailedForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Purchase order not found"));
    }

    private AppUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email)
                .orElseThrow(ApiException::unauthorized);
    }

    private void transition(PurchaseOrder order, Consumer<PurchaseOrder> action) {
        try {
            action.accept(order);
        } catch (IllegalStateException exception) {
            throw ApiException.conflict(exception.getMessage());
        }
    }

    private String nextOrderNumber() {
        String suffix = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 16).toUpperCase(Locale.ROOT);
        return "PO-" + suffix;
    }

    private String receiptRequestHash(long orderId, PurchaseOrderDtos.ReceiveRequest request) {
        StringBuilder canonical = new StringBuilder();
        addHashField(canonical, Long.toString(orderId));
        addHashField(canonical, clean(request.deliveryReference()));
        addHashField(canonical, clean(request.notes()));
        request.items().stream()
                .sorted(Comparator.comparing(PurchaseOrderDtos.ReceiveLineRequest::purchaseOrderItemId))
                .forEach(line -> {
                    addHashField(canonical, Long.toString(line.purchaseOrderItemId()));
                    addHashField(canonical, line.quantity().stripTrailingZeros().toPlainString());
                });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable", exception);
        }
    }

    private void addHashField(StringBuilder canonical, String value) {
        String field = value == null ? "" : value;
        canonical.append(field.length()).append(':').append(field).append('|');
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
