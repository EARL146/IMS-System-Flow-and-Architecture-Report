package com.ims.api.pos;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.catalog.Product;
import com.ims.api.catalog.ProductRepository;
import com.ims.api.catalog.TrackingType;
import com.ims.api.common.ApiException;
import com.ims.api.common.PageResponse;
import com.ims.api.config.CheckoutProperties;
import com.ims.api.inventory.InventoryBalanceRepository;
import com.ims.api.inventory.InventoryService;
import com.ims.api.warehouse.Warehouse;
import com.ims.api.warehouse.WarehouseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class PosService {
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(4);

    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final InventoryBalanceRepository balances;
    private final InventoryService inventory;
    private final AppUserRepository users;
    private final PosSaleRepository sales;
    private final PosSaleIdempotencyRepository idempotencyKeys;
    private final CheckoutProperties checkoutProperties;

    public PosService(
            ProductRepository products,
            WarehouseRepository warehouses,
            InventoryBalanceRepository balances,
            InventoryService inventory,
            AppUserRepository users,
            PosSaleRepository sales,
            PosSaleIdempotencyRepository idempotencyKeys,
            CheckoutProperties checkoutProperties
    ) {
        this.products = products;
        this.warehouses = warehouses;
        this.balances = balances;
        this.inventory = inventory;
        this.users = users;
        this.sales = sales;
        this.idempotencyKeys = idempotencyKeys;
        this.checkoutProperties = checkoutProperties;
    }

    @Transactional(readOnly = true)
    public PosDtos.ConfigResponse config() {
        validateTaxAndCurrency();
        return new PosDtos.ConfigResponse(checkoutProperties.taxRate(),
                checkoutProperties.currencyCode().toUpperCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public PageResponse<PosDtos.ProductOptionResponse> searchProducts(
            long warehouseId,
            String query,
            int page,
            int size
    ) {
        warehouses.findById(warehouseId).filter(Warehouse::isActive)
                .orElseThrow(() -> ApiException.notFound("Active warehouse not found"));
        var result = products.searchCatalog(clean(query), PageRequest.of(page, size, Sort.by("name").ascending()));
        if (result.isEmpty()) return PageResponse.from(result.map(product -> PosDtos.ProductOptionResponse.from(product, null)));

        var productIds = result.getContent().stream().map(Product::getId).toList();
        Map<Long, BigDecimal> availableByProduct = new HashMap<>();
        balances.findAvailableByProductIds(warehouseId, productIds).forEach(stock ->
                availableByProduct.put(stock.getProductId(), stock.getAvailableQuantity()));
        return PageResponse.from(result.map(product -> PosDtos.ProductOptionResponse.from(product,
                availableByProduct.getOrDefault(product.getId(), BigDecimal.ZERO.setScale(3)))));
    }

    @Transactional(readOnly = true)
    public PageResponse<PosDtos.CustomerOptionResponse> searchCustomers(String query, int page, int size) {
        return PageResponse.from(users.searchCustomers(clean(query), PageRequest.of(page, size))
                .map(PosDtos.CustomerOptionResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<PosDtos.SaleResponse> listSales(int page, int size) {
        return PageResponse.from(sales.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(PosDtos.SaleResponse::from));
    }

    @Transactional(readOnly = true)
    public PosDtos.SaleResponse getSale(long saleId) {
        return PosDtos.SaleResponse.from(loadDetailed(saleId));
    }

    @Transactional
    public PosDtos.SaleResponse createSale(PosDtos.CreateSaleRequest request, String cashierEmail) {
        AppUser cashier = users.findByEmailIgnoreCase(cashierEmail).orElseThrow(ApiException::unauthorized);
        String key = request.idempotencyKey().toString();
        String requestHash = requestHash(request);
        if (idempotencyKeys.reserveIfAbsent(key, requestHash) == 0) {
            PosSaleIdempotencyKey existing = idempotencyKeys.findForUpdate(key)
                    .orElseThrow(() -> new IllegalStateException("POS idempotency reservation disappeared"));
            if (!existing.getRequestHash().equals(requestHash)) {
                throw ApiException.conflict("POS idempotency key was already used for a different sale");
            }
            if (existing.getSale() == null) {
                throw new IllegalStateException("POS idempotency reservation has no completed sale");
            }
            return PosDtos.SaleResponse.from(loadDetailed(existing.getSale().getId()));
        }

        Warehouse warehouse = warehouses.findById(request.warehouseId())
                .filter(Warehouse::isActive)
                .orElseThrow(() -> ApiException.notFound("Active warehouse not found"));
        AppUser customer = resolveCustomer(request.customerEmail());
        validateTaxAndCurrency();
        if (request.items().size() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A sale can contain at most 100 products");
        }
        HashSet<Long> productIds = new HashSet<>();
        BigDecimal subtotal = ZERO_MONEY;
        ArrayList<PosSaleItem> saleItems = new ArrayList<>();
        short lineNumber = 1;
        for (PosDtos.LineRequest line : request.items()) {
            if (!productIds.add(line.productId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "A product can appear only once in a sale");
            }
            if (line.quantity().signum() <= 0 || line.quantity().stripTrailingZeros().scale() > 3) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Sale quantities must be positive and have at most three decimal places");
            }
            Product product = products.findWithCategoryById(line.productId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> ApiException.notFound("Active product not found: " + line.productId()));
            BigDecimal unitPrice = product.getSellingPrice().setScale(4, RoundingMode.HALF_UP);
            BigDecimal lineTotal = unitPrice.multiply(line.quantity()).setScale(4, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            saleItems.add(new PosSaleItem(lineNumber++, product, line.quantity(), unitPrice,
                    product.getCostPrice().setScale(4, RoundingMode.HALF_UP), lineTotal));
        }

        subtotal = subtotal.setScale(4, RoundingMode.HALF_UP);
        BigDecimal discount = request.discountAmount().setScale(4, RoundingMode.HALF_UP);
        if (discount.signum() < 0 || discount.compareTo(subtotal) >= 0) {
            throw ApiException.conflict("Discount must be non-negative and less than the sale subtotal");
        }
        BigDecimal taxableSubtotal = subtotal.subtract(discount).setScale(4, RoundingMode.HALF_UP);
        BigDecimal tax = taxableSubtotal.multiply(checkoutProperties.taxRate()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal total = taxableSubtotal.add(tax).setScale(4, RoundingMode.HALF_UP);
        if (total.signum() <= 0) throw ApiException.conflict("A POS sale total must be greater than zero");

        String paymentReference = clean(request.paymentReference());
        BigDecimal tendered = request.amountTendered() == null
                ? null : request.amountTendered().setScale(4, RoundingMode.HALF_UP);
        BigDecimal change = ZERO_MONEY;
        if (request.paymentMethod() == PosPaymentMethod.CASH) {
            if (tendered == null || tendered.compareTo(total) < 0) {
                throw ApiException.conflict("Cash received must be at least the sale total");
            }
            if (paymentReference != null) {
                throw ApiException.conflict("Cash sales cannot include an external terminal reference");
            }
            change = tendered.subtract(total).setScale(4, RoundingMode.HALF_UP);
        } else if (request.paymentMethod() == PosPaymentMethod.CARD_TERMINAL) {
            if (tendered != null) {
                throw ApiException.conflict("Do not enter card numbers or card tender details in the POS");
            }
            if (paymentReference == null) {
                throw ApiException.conflict("Enter the approved external terminal reference");
            }
        }

        PosSale sale = new PosSale(nextSaleNumber(), cashier, warehouse, customer, request.paymentMethod(),
                paymentReference, checkoutProperties.currencyCode().toUpperCase(Locale.ROOT),
                subtotal, discount, tax, total, tendered, change);
        saleItems.forEach(sale::addItem);
        PosSale saved = sales.saveAndFlush(sale);

        for (PosSaleItem item : saved.getItems()) {
            inventory.recordPosSaleStock(item.getProduct().getId(), warehouse.getId(), item.getQuantity(),
                    cashierEmail, movementKey(saved.getId(), item.getId()), saved.getSaleNumber());
        }

        PosSaleIdempotencyKey reservation = idempotencyKeys.findForUpdate(key)
                .orElseThrow(() -> new IllegalStateException("POS idempotency reservation could not be reloaded"));
        reservation.setSale(saved);
        idempotencyKeys.saveAndFlush(reservation);
        return PosDtos.SaleResponse.from(loadDetailed(saved.getId()));
    }

    private AppUser resolveCustomer(String email) {
        String normalized = clean(email);
        if (normalized == null) return null;
        AppUser customer = users.findByEmailIgnoreCase(normalized)
                .filter(AppUser::isEnabled)
                .orElseThrow(() -> ApiException.notFound("Customer account not found"));
        if (customer.getRoles().stream().noneMatch(role -> role.getCode().equals("CUSTOMER"))) {
            throw ApiException.notFound("Customer account not found");
        }
        return customer;
    }

    private PosSale loadDetailed(long saleId) {
        return sales.findDetailedById(saleId).orElseThrow(() -> ApiException.notFound("Sale not found"));
    }

    private String nextSaleNumber() {
        return "POS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String movementKey(long saleId, long itemId) {
        return UUID.nameUUIDFromBytes(("ims:pos:" + saleId + ":" + itemId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String requestHash(PosDtos.CreateSaleRequest request) {
        String lines = request.items().stream()
                .map(line -> line.productId() + ":" + canonical(line.quantity()))
                .reduce((left, right) -> left + ";" + right).orElse("");
        String canonical = request.warehouseId() + "|"
                + (clean(request.customerEmail()) == null ? "" : clean(request.customerEmail()).toLowerCase(Locale.ROOT)) + "|"
                + lines + "|" + canonical(request.discountAmount()) + "|" + request.paymentMethod().name() + "|"
                + (request.amountTendered() == null ? "" : canonical(request.amountTendered())) + "|"
                + (clean(request.paymentReference()) == null ? "" : clean(request.paymentReference()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required SHA-256 algorithm is unavailable", exception);
        }
    }

    private String canonical(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateTaxAndCurrency() {
        if (checkoutProperties.taxRate().signum() < 0 || checkoutProperties.taxRate().compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalStateException("POS tax rate must be between zero and one");
        }
        if (!checkoutProperties.currencyCode().matches("[A-Za-z]{3}")) {
            throw new IllegalStateException("POS currency code must contain three letters");
        }
    }
}
