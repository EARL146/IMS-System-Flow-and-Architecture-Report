package com.ims.api.inventory;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.catalog.Product;
import com.ims.api.catalog.ProductRepository;
import com.ims.api.catalog.TrackingType;
import com.ims.api.common.ApiException;
import com.ims.api.warehouse.Warehouse;
import com.ims.api.warehouse.WarehouseRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

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

        AppUser actor = users.findByEmailIgnoreCase(actorEmail).orElse(null);
        enforceWarehouseScope(actor, warehouse.getId());

        balances.ensureExists(product.getId(), warehouse.getId());
        InventoryBalance balance = balances.findForUpdate(product.getId(), warehouse.getId())
                .orElseThrow(() -> new IllegalStateException("Inventory balance could not be locked"));

        BigDecimal before = balance.getOnHandQuantity();
        BigDecimal after = before.add(delta);
        if (after.compareTo(balance.getReservedQuantity().add(balance.getDamagedQuantity())) < 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Adjustment would reduce sellable stock below reserved and damaged quantities");
        }

        balance.setOnHandQuantity(after);
        balances.save(balance);

        InventoryMovement movement = new InventoryMovement(
                product, warehouse, actor, delta, before, after, idempotencyKey, request.reason().trim());
        return InventoryDtos.MovementResponse.from(movements.save(movement));
    }

    @Transactional
    public InventoryDtos.TransferResponse transfer(
            InventoryDtos.TransferRequest request,
            String actorEmail
    ) {
        if (Objects.equals(request.sourceWarehouseId(), request.destinationWarehouseId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Source and destination warehouses must be different");
        }
        BigDecimal quantity = request.quantity();
        if (quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Transfer quantity must be positive with at most three decimal places");
        }

        String outKey = request.idempotencyKey().toString();
        String inKey = UUID.nameUUIDFromBytes(("ims:transfer:in:" + outKey).getBytes(StandardCharsets.UTF_8)).toString();
        String trimmedReason = request.reason().trim();

        if (idempotencyKeys.reserveIfAbsent(outKey) == 0) {
            InventoryMovement existingOut = movements.findByIdempotencyKey(outKey)
                    .orElseThrow(() -> new IllegalStateException("Transfer outbound idempotency reservation has no movement"));
            InventoryMovement existingIn = movements.findByIdempotencyKey(inKey)
                    .orElseThrow(() -> new IllegalStateException("Transfer inbound idempotency reservation has no movement"));
            boolean sameRequest = "TRANSFER_OUT".equals(existingOut.getMovementType())
                    && "TRANSFER_IN".equals(existingIn.getMovementType())
                    && existingOut.getProduct().getId().equals(request.productId())
                    && existingOut.getWarehouse().getId().equals(request.sourceWarehouseId())
                    && existingIn.getWarehouse().getId().equals(request.destinationWarehouseId())
                    && existingOut.getQuantityDelta().compareTo(quantity.negate()) == 0
                    && existingIn.getQuantityDelta().compareTo(quantity) == 0
                    && existingOut.getReason().equals(trimmedReason);
            if (!sameRequest) {
                throw ApiException.conflict("Idempotency key was already used for a different warehouse transfer");
            }
            return new InventoryDtos.TransferResponse(
                    existingOut.getReferenceId(),
                    InventoryDtos.MovementResponse.from(existingOut),
                    InventoryDtos.MovementResponse.from(existingIn)
            );
        }
        idempotencyKeys.reserveIfAbsent(inKey);

        Product product = products.findById(request.productId())
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (product.getTrackingType() == TrackingType.NONE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Non-tracked products cannot be transferred between warehouses");
        }

        Warehouse sourceWarehouse = warehouses.findById(request.sourceWarehouseId())
                .orElseThrow(() -> ApiException.notFound("Source warehouse not found"));
        Warehouse destinationWarehouse = warehouses.findById(request.destinationWarehouseId())
                .orElseThrow(() -> ApiException.notFound("Destination warehouse not found"));
        if (!sourceWarehouse.isActive() || !destinationWarehouse.isActive()) {
            throw ApiException.conflict("Both source and destination warehouses must be active");
        }

        AppUser actor = users.findByEmailIgnoreCase(actorEmail).orElse(null);
        enforceWarehouseScope(actor, sourceWarehouse.getId());

        long firstWarehouseId = Math.min(sourceWarehouse.getId(), destinationWarehouse.getId());
        long secondWarehouseId = Math.max(sourceWarehouse.getId(), destinationWarehouse.getId());
        balances.ensureExists(product.getId(), firstWarehouseId);
        balances.ensureExists(product.getId(), secondWarehouseId);

        InventoryBalance firstLocked = balances.findForUpdate(product.getId(), firstWarehouseId)
                .orElseThrow(() -> new IllegalStateException("First warehouse balance could not be locked"));
        InventoryBalance secondLocked = balances.findForUpdate(product.getId(), secondWarehouseId)
                .orElseThrow(() -> new IllegalStateException("Second warehouse balance could not be locked"));

        InventoryBalance sourceBalance = firstLocked.getWarehouse().getId().equals(sourceWarehouse.getId()) ? firstLocked : secondLocked;
        InventoryBalance destinationBalance = firstLocked.getWarehouse().getId().equals(destinationWarehouse.getId()) ? firstLocked : secondLocked;

        if (sourceBalance.getAvailableQuantity().compareTo(quantity) < 0) {
            throw ApiException.conflict("There is not enough available stock in the source warehouse for this transfer");
        }

        BigDecimal sourceBefore = sourceBalance.getOnHandQuantity();
        BigDecimal sourceAfter = sourceBefore.subtract(quantity);
        BigDecimal destBefore = destinationBalance.getOnHandQuantity();
        BigDecimal destAfter = destBefore.add(quantity);

        sourceBalance.setOnHandQuantity(sourceAfter);
        destinationBalance.setOnHandQuantity(destAfter);
        balances.save(sourceBalance);
        balances.save(destinationBalance);

        String transferReference = "TRF-" + outKey.replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        InventoryMovement outMovement = movements.save(new InventoryMovement(
                product, sourceWarehouse, actor, "TRANSFER_OUT", quantity.negate(),
                sourceBefore, sourceAfter, outKey, trimmedReason, "WAREHOUSE_TRANSFER", transferReference));
        InventoryMovement inMovement = movements.save(new InventoryMovement(
                product, destinationWarehouse, actor, "TRANSFER_IN", quantity,
                destBefore, destAfter, inKey, trimmedReason, "WAREHOUSE_TRANSFER", transferReference));

        return new InventoryDtos.TransferResponse(
                transferReference,
                InventoryDtos.MovementResponse.from(outMovement),
                InventoryDtos.MovementResponse.from(inMovement)
        );
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
        enforceWarehouseScope(actor, warehouseId);

        balance.setOnHandQuantity(after);
        balances.save(balance);
        movements.save(new InventoryMovement(product, warehouse, actor, "PURCHASE_RECEIPT", quantity,
                before, after, idempotencyKey, reason, "GOODS_RECEIPT", goodsReceiptId));
    }

    @Transactional
    public void dispatchSupplierReturnStock(
            long productId,
            long warehouseId,
            BigDecimal quantity,
            BigDecimal damagedQuantity,
            String actorEmail,
            String idempotencyKey,
            String returnNumber,
            Long returnItemId
    ) {
        if (quantity.signum() <= 0 || quantity.stripTrailingZeros().scale() > 3
                || damagedQuantity.signum() < 0 || damagedQuantity.compareTo(quantity) > 0
                || damagedQuantity.stripTrailingZeros().scale() > 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Supplier return quantities are invalid");
        }

        Product product = products.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        Warehouse warehouse = warehouses.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Warehouse not found"));
        if (product.getTrackingType() == TrackingType.NONE) return;

        if (idempotencyKeys.reserveIfAbsent(idempotencyKey) == 0) {
            InventoryMovement existing = movements.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Supplier return movement idempotency reservation has no movement"));
            boolean sameRequest = existing.getMovementType().equals("SUPPLIER_RETURN")
                    && existing.getProduct().getId().equals(productId)
                    && existing.getWarehouse().getId().equals(warehouseId)
                    && existing.getQuantityDelta().compareTo(quantity.negate()) == 0
                    && existing.getDamagedQuantityDelta().compareTo(damagedQuantity.negate()) == 0
                    && java.util.Objects.equals(existing.getReferenceId(), returnItemId.toString());
            if (!sameRequest) {
                throw ApiException.conflict("Inventory idempotency key was already used for a different supplier return movement");
            }
            return;
        }

        InventoryBalance balance = balances.findForUpdate(productId, warehouseId)
                .orElseThrow(() -> ApiException.conflict("There is not enough stock to dispatch this supplier return"));
        BigDecimal sellableQuantity = quantity.subtract(damagedQuantity);
        if (balance.getOnHandQuantity().subtract(quantity).compareTo(balance.getReservedQuantity()) < 0
                || balance.getDamagedQuantity().compareTo(damagedQuantity) < 0
                || balance.getAvailableQuantity().compareTo(sellableQuantity) < 0) {
            throw ApiException.conflict("There is not enough unreserved stock in the selected condition to dispatch this supplier return");
        }

        AppUser actor = users.findByEmailIgnoreCase(actorEmail).orElseThrow(ApiException::unauthorized);
        enforceWarehouseScope(actor, warehouseId);
        BigDecimal before = balance.getOnHandQuantity();
        BigDecimal after = before.subtract(quantity);
        balance.setOnHandQuantity(after);
        balance.setDamagedQuantity(balance.getDamagedQuantity().subtract(damagedQuantity));
        balances.save(balance);
        movements.save(new InventoryMovement(product, warehouse, actor, "SUPPLIER_RETURN", quantity.negate(),
                before, after, idempotencyKey, "Supplier return dispatched " + returnNumber,
                "SUPPLIER_RETURN", returnItemId.toString(), BigDecimal.ZERO, damagedQuantity.negate()));
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
        enforceWarehouseScope(actor, warehouseId);
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

    @Transactional
    public void recordPosSaleStock(
            long productId,
            long warehouseId,
            BigDecimal quantity,
            String actorEmail,
            String idempotencyKey,
            String saleNumber
    ) {
        Product product = products.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (product.getTrackingType() == TrackingType.NONE) return;
        Warehouse warehouse = warehouses.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Warehouse not found"));
        InventoryBalance balance = balances.findForUpdate(productId, warehouseId)
                .orElseThrow(() -> ApiException.conflict("There is not enough available stock for this sale"));
        if (balance.getAvailableQuantity().compareTo(quantity) < 0) {
            throw ApiException.conflict("There is not enough available stock to complete this sale");
        }

        AppUser actor = users.findByEmailIgnoreCase(actorEmail)
                .orElseThrow(ApiException::unauthorized);
        enforceWarehouseScope(actor, warehouseId);
        BigDecimal before = balance.getOnHandQuantity();
        BigDecimal after = before.subtract(quantity);
        balance.setOnHandQuantity(after);
        balances.save(balance);
        movements.save(new InventoryMovement(product, warehouse, actor, "POS_SALE", quantity.negate(),
                before, after, idempotencyKey, "Point-of-sale transaction " + saleNumber,
                "POS_SALE", saleNumber, BigDecimal.ZERO));
    }

    @Transactional
    public void recordReturnInspectionStock(
            long productId,
            long warehouseId,
            BigDecimal restockQuantity,
            BigDecimal damagedQuantity,
            String actorEmail,
            String restockIdempotencyKey,
            String damagedIdempotencyKey,
            String returnNumber
    ) {
        Product product = products.findById(productId)
                .orElseThrow(() -> ApiException.notFound("Product not found"));
        if (product.getTrackingType() == TrackingType.NONE) return;
        if (restockQuantity.signum() < 0 || damagedQuantity.signum() < 0) {
            throw new IllegalArgumentException("Return disposition quantities cannot be negative");
        }
        if (restockQuantity.signum() == 0 && damagedQuantity.signum() == 0) return;

        Warehouse warehouse = warehouses.findById(warehouseId)
                .orElseThrow(() -> ApiException.notFound("Warehouse not found"));
        InventoryBalance balance = balances.findForUpdate(productId, warehouseId)
                .orElseThrow(() -> ApiException.conflict("The original inventory balance could not be found"));
        AppUser actor = users.findByEmailIgnoreCase(actorEmail).orElseThrow(ApiException::unauthorized);
        enforceWarehouseScope(actor, warehouseId);
        BigDecimal before = balance.getOnHandQuantity();
        BigDecimal damagedBefore = balance.getDamagedQuantity();
        BigDecimal quantityAdded = restockQuantity.add(damagedQuantity);
        BigDecimal after = before.add(quantityAdded);
        balance.setOnHandQuantity(after);
        balance.setDamagedQuantity(damagedBefore.add(damagedQuantity));
        balances.save(balance);

        if (restockQuantity.signum() > 0) {
            movements.save(new InventoryMovement(product, warehouse, actor, "RETURN_RESTOCK",
                    restockQuantity, before, before.add(restockQuantity), restockIdempotencyKey,
                    "Inspected return restocked " + returnNumber, "RETURN", returnNumber,
                    BigDecimal.ZERO, BigDecimal.ZERO));
        }
        if (damagedQuantity.signum() > 0) {
            movements.save(new InventoryMovement(product, warehouse, actor, "RETURN_DAMAGED",
                    damagedQuantity, before.add(restockQuantity), after, damagedIdempotencyKey,
                    "Inspected return quarantined as damaged " + returnNumber, "RETURN", returnNumber,
                    BigDecimal.ZERO, damagedQuantity));
        }
    }

    private void enforceWarehouseScope(AppUser actor, long warehouseId) {
        if (actor != null && !actor.canAccessWarehouse(warehouseId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Your account is not scoped to operate on this warehouse");
        }
    }
}
