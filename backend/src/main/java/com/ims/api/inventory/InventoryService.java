package com.ims.api.inventory;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.catalog.Product;
import com.ims.api.catalog.ProductRepository;
import com.ims.api.catalog.TrackingType;
import com.ims.api.common.ApiException;
import com.ims.api.warehouse.Warehouse;
import com.ims.api.warehouse.WarehouseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
public class InventoryService {
    private final InventoryBalanceRepository balances;
    private final InventoryMovementRepository movements;
    private final InventoryIdempotencyRepository idempotencyKeys;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final AppUserRepository users;

    public InventoryService(
            InventoryBalanceRepository balances,
            InventoryMovementRepository movements,
            InventoryIdempotencyRepository idempotencyKeys,
            ProductRepository products,
            WarehouseRepository warehouses,
            AppUserRepository users
    ) {
        this.balances = balances;
        this.movements = movements;
        this.idempotencyKeys = idempotencyKeys;
        this.products = products;
        this.warehouses = warehouses;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Page<InventoryDtos.StockResponse> stock(long warehouseId, Pageable pageable) {
        if (!warehouses.existsById(warehouseId)) {
            throw ApiException.notFound("Warehouse not found");
        }
        return balances.findAllForWarehouse(warehouseId, pageable).map(InventoryDtos.StockResponse::from);
    }

    @Transactional(readOnly = true)
    public InventoryDtos.StockSummary summary() {
        return new InventoryDtos.StockSummary(balances.totalOnHand(), balances.totalAvailable());
    }

    @Transactional(readOnly = true)
    public Page<InventoryDtos.MovementResponse> recentMovements(long warehouseId, Pageable pageable) {
        if (!warehouses.existsById(warehouseId)) {
            throw ApiException.notFound("Warehouse not found");
        }
        return movements.findRecentForWarehouse(warehouseId, pageable).map(InventoryDtos.MovementResponse::from);
    }

    @Transactional
    public InventoryDtos.MovementResponse adjust(
            InventoryDtos.AdjustmentRequest request,
            String actorEmail
    ) {
        BigDecimal delta = request.quantityDelta();
        if (delta.signum() == 0 || delta.stripTrailingZeros().scale() > 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Quantity change must be non-zero with at most three decimal places");
        }

        String idempotencyKey = request.idempotencyKey().toString();
        if (idempotencyKeys.reserveIfAbsent(idempotencyKey) == 0) {
            InventoryMovement movement = movements.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Inventory idempotency reservation has no movement"));
            boolean sameRequest = movement.getProduct().getId().equals(request.productId())
                    && movement.getWarehouse().getId().equals(request.warehouseId())
                    && movement.getQuantityDelta().compareTo(delta) == 0
                    && movement.getReason().equals(request.reason().trim());
            if (!sameRequest) {
                throw ApiException.conflict("Idempotency key was already used for a different adjustment");
            }
            return InventoryDtos.MovementResponse.from(movement);
        }

        Product product = products.findById(request.productId())
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        Warehouse warehouse = warehouses.findById(request.warehouseId())
                .orElseThrow(() -> ApiException.notFound("Warehouse not found"));

        balances.ensureExists(product.getId(), warehouse.getId());
        InventoryBalance balance = balances.findForUpdate(product.getId(), warehouse.getId())
                .orElseThrow(() -> new IllegalStateException("Inventory balance could not be locked"));

        BigDecimal before = balance.getOnHandQuantity();
        BigDecimal after = before.add(delta);
        if (after.compareTo(balance.getReservedQuantity().add(balance.getDamagedQuantity())) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Adjustment would reduce sellable stock below reserved and damaged quantities");
        }

        AppUser actor = users.findByEmailIgnoreCase(actorEmail).orElse(null);
        balance.setOnHandQuantity(after);
        balances.save(balance);

        InventoryMovement movement = new InventoryMovement(
                product, warehouse, actor, delta, before, after, idempotencyKey, request.reason().trim());
        return InventoryDtos.MovementResponse.from(movements.save(movement));
    }

    @Transactional
    public void receivePurchaseOrderStock(
            long productId,
            long warehouseId,
            BigDecimal quantity,
            String actorEmail,
            String idempotencyKey,
            String purchaseOrderNumber,
            String goodsReceiptId
    ) {
        if (quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Received quantity must be positive with at most three decimal places");
        }

        Product product = products.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        Warehouse warehouse = warehouses.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Warehouse not found"));
        if (product.getTrackingType() == TrackingType.NONE) return;

        String reason = "Goods receipt for purchase order " + purchaseOrderNumber;
        if (idempotencyKeys.reserveIfAbsent(idempotencyKey) == 0) {
            InventoryMovement movement = movements.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Inventory idempotency reservation has no movement"));
            boolean sameRequest = movement.getMovementType().equals("PURCHASE_RECEIPT")
                    && movement.getProduct().getId().equals(productId)
                    && movement.getWarehouse().getId().equals(warehouseId)
                    && movement.getQuantityDelta().compareTo(quantity) == 0
                    && Objects.equals(movement.getReferenceId(), goodsReceiptId);
            if (!sameRequest) {
                throw ApiException.conflict("Inventory idempotency key was already used for a different movement");
            }
            return;
        }

        balances.ensureExists(productId, warehouseId);
        InventoryBalance balance = balances.findForUpdate(productId, warehouseId)
                .orElseThrow(() -> new IllegalStateException("Inventory balance could not be locked"));
        BigDecimal before = balance.getOnHandQuantity();
        BigDecimal after = before.add(quantity);
        AppUser actor = users.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(ApiException::unauthorized);

        balance.setOnHandQuantity(after);
        balances.save(balance);
        movements.save(new InventoryMovement(product, warehouse, actor, "PURCHASE_RECEIPT", quantity,
                before, after, idempotencyKey, reason, "GOODS_RECEIPT", goodsReceiptId));
    }

    @Transactional
    public void reserveCustomerOrderStock(
            long productId,
            long warehouseId,
            BigDecimal quantity,
            String customerEmail,
            String idempotencyKey,
            String orderNumber
    ) {
        Product product = products.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (product.getTrackingType() == TrackingType.NONE) return;
        Warehouse warehouse = warehouses.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Warehouse not found"));

        balances.ensureExists(productId, warehouseId);
        InventoryBalance balance = balances.findForUpdate(productId, warehouseId)
                .orElseThrow(() -> new IllegalStateException("Inventory balance could not be locked"));
        if (balance.getAvailableQuantity().compareTo(quantity) < 0) {
            throw ApiException.conflict("There is not enough available stock to complete this order");
        }

        AppUser actor = users.findByEmailIgnoreCase(customerEmail)
                .orElseThrow(ApiException::unauthorized);
        BigDecimal reservedBefore = balance.getReservedQuantity();
        BigDecimal reservedAfter = reservedBefore.add(quantity);
        BigDecimal onHand = balance.getOnHandQuantity();
        balance.setReservedQuantity(reservedAfter);
        balances.save(balance);
        movements.save(new InventoryMovement(product, warehouse, actor, "SALE_RESERVATION",
                BigDecimal.ZERO, onHand, onHand, idempotencyKey,
                "Stock reserved for customer order " + orderNumber,
                "CUSTOMER_ORDER", orderNumber, reservedAfter.subtract(reservedBefore)));
    }

    @Transactional
    public void releaseCustomerOrderStock(
            long productId,
            long warehouseId,
            BigDecimal quantity,
            String customerEmail,
            String idempotencyKey,
            String orderNumber
    ) {
        Product product = products.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (product.getTrackingType() == TrackingType.NONE) return;
        Warehouse warehouse = warehouses.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Warehouse not found"));
        InventoryBalance balance = balances.findForUpdate(productId, warehouseId)
                .orElseThrow(() -> new IllegalStateException("Reserved stock balance could not be found"));
        if (balance.getReservedQuantity().compareTo(quantity) < 0) {
            throw new IllegalStateException("Reserved stock is lower than the order quantity");
        }

        AppUser actor = users.findByEmailIgnoreCase(customerEmail)
                .orElseThrow(ApiException::unauthorized);
        BigDecimal reservedBefore = balance.getReservedQuantity();
        BigDecimal reservedAfter = reservedBefore.subtract(quantity);
        BigDecimal onHand = balance.getOnHandQuantity();
        balance.setReservedQuantity(reservedAfter);
        balances.save(balance);
        movements.save(new InventoryMovement(product, warehouse, actor, "SALE_RESERVATION_RELEASE",
                BigDecimal.ZERO, onHand, onHand, idempotencyKey,
                "Reserved stock released for customer order " + orderNumber,
                "CUSTOMER_ORDER", orderNumber, reservedAfter.subtract(reservedBefore)));
    }

    @Transactional
    public void fulfillCustomerOrderStock(
            long productId,
            long warehouseId,
            BigDecimal quantity,
            String actorEmail,
            String idempotencyKey,
            String orderNumber
    ) {
        Product product = products.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (product.getTrackingType() == TrackingType.NONE) return;
        Warehouse warehouse = warehouses.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Warehouse not found"));
        InventoryBalance balance = balances.findForUpdate(productId, warehouseId)
                .orElseThrow(() -> new IllegalStateException("Reserved stock balance could not be found"));
        if (balance.getReservedQuantity().compareTo(quantity) < 0
                || balance.getOnHandQuantity().compareTo(quantity) < 0) {
            throw new IllegalStateException("Reserved stock is lower than the order quantity");
        }

        AppUser actor = users.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(ApiException::unauthorized);
        BigDecimal onHandBefore = balance.getOnHandQuantity();
        BigDecimal onHandAfter = onHandBefore.subtract(quantity);
        BigDecimal reservedAfter = balance.getReservedQuantity().subtract(quantity);
        balance.setOnHandQuantity(onHandAfter);
        balance.setReservedQuantity(reservedAfter);
        balances.save(balance);
        movements.save(new InventoryMovement(product, warehouse, actor, "SALE_FULFILLMENT",
                quantity.negate(), onHandBefore, onHandAfter, idempotencyKey,
                "Customer order fulfilled " + orderNumber,
                "CUSTOMER_ORDER", orderNumber, quantity.negate()));
    }
}
