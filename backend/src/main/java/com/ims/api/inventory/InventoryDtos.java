package com.ims.api.inventory;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {
    private InventoryDtos() {
    }

    public record AdjustmentRequest(
            @NotNull Long productId,
            @NotNull Long warehouseId,
            @NotNull @Digits(integer = 15, fraction = 3) BigDecimal quantityDelta,
            @NotNull UUID idempotencyKey,
            @NotBlank @Size(max = 240) String reason
    ) {
    }

    public record StockSummary(BigDecimal totalOnHand, BigDecimal totalAvailable) {
    }

    public record StockResponse(
            Long productId,
            String sku,
            String productName,
            Long warehouseId,
            String warehouseName,
            BigDecimal onHand,
            BigDecimal reserved,
            BigDecimal damaged,
            BigDecimal available
    ) {
        static StockResponse from(InventoryBalance balance) {
            return new StockResponse(
                    balance.getProduct().getId(), balance.getProduct().getSku(), balance.getProduct().getName(),
                    balance.getWarehouse().getId(), balance.getWarehouse().getName(),
                    balance.getOnHandQuantity(), balance.getReservedQuantity(), balance.getDamagedQuantity(),
                    balance.getAvailableQuantity());
        }
    }

    public record MovementResponse(
            Long id,
            Long productId,
            String sku,
            String productName,
            Long warehouseId,
            String warehouseName,
            String movementType,
            BigDecimal quantityDelta,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            String idempotencyKey,
            String reason,
            Instant occurredAt
    ) {
        static MovementResponse from(InventoryMovement movement) {
            return new MovementResponse(
                    movement.getId(), movement.getProduct().getId(), movement.getProduct().getSku(),
                    movement.getProduct().getName(), movement.getWarehouse().getId(),
                    movement.getWarehouse().getName(), movement.getMovementType(), movement.getQuantityDelta(),
                    movement.getQuantityBefore(), movement.getQuantityAfter(), movement.getIdempotencyKey(),
                    movement.getReason(), movement.getOccurredAt());
        }
    }
}
