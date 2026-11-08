package com.ims.api.inventory;

import com.ims.api.auth.AppUser;
import com.ims.api.catalog.Product;
import com.ims.api.warehouse.Warehouse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private AppUser actor;

    @Column(name = "movement_type", nullable = false, length = 32)
    private String movementType;

    @Column(name = "quantity_delta", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityDelta;

    @Column(name = "reserved_quantity_delta", nullable = false, precision = 18, scale = 3)
    private BigDecimal reservedQuantityDelta = BigDecimal.ZERO;

    @Column(name = "damaged_quantity_delta", nullable = false, precision = 18, scale = 3)
    private BigDecimal damagedQuantityDelta = BigDecimal.ZERO;

    @Column(name = "quantity_before", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityBefore;

    @Column(name = "quantity_after", nullable = false, precision = 18, scale = 3)
    private BigDecimal quantityAfter;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String idempotencyKey;

    @Column(nullable = false, length = 240)
    private String reason;

    @Column(name = "reference_type", length = 48)
    private String referenceType;

    @Column(name = "reference_id", length = 80)
    private String referenceId;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "occurred_at", nullable = false, insertable = false, updatable = false)
    private Instant occurredAt;

    protected InventoryMovement() {
    }

    public InventoryMovement(
            Product product,
            Warehouse warehouse,
            AppUser actor,
            BigDecimal quantityDelta,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            String idempotencyKey,
            String reason
    ) {
        this(product, warehouse, actor, "ADJUSTMENT", quantityDelta, quantityBefore, quantityAfter,
                idempotencyKey, reason, null, null, BigDecimal.ZERO);
    }

    public InventoryMovement(
            Product product,
            Warehouse warehouse,
            AppUser actor,
            String movementType,
            BigDecimal quantityDelta,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            String idempotencyKey,
            String reason,
            String referenceType,
            String referenceId
    ) {
        this(product, warehouse, actor, movementType, quantityDelta, quantityBefore, quantityAfter,
                idempotencyKey, reason, referenceType, referenceId, BigDecimal.ZERO);
    }

    public InventoryMovement(
            Product product,
            Warehouse warehouse,
            AppUser actor,
            String movementType,
            BigDecimal quantityDelta,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            String idempotencyKey,
            String reason,
            String referenceType,
            String referenceId,
            BigDecimal reservedQuantityDelta
    ) {
        this(product, warehouse, actor, movementType, quantityDelta, quantityBefore, quantityAfter,
                idempotencyKey, reason, referenceType, referenceId, reservedQuantityDelta, BigDecimal.ZERO);
    }

    public InventoryMovement(
            Product product,
            Warehouse warehouse,
            AppUser actor,
            String movementType,
            BigDecimal quantityDelta,
            BigDecimal quantityBefore,
            BigDecimal quantityAfter,
            String idempotencyKey,
            String reason,
            String referenceType,
            String referenceId,
            BigDecimal reservedQuantityDelta,
            BigDecimal damagedQuantityDelta
    ) {
        this.product = product;
        this.warehouse = warehouse;
        this.actor = actor;
        this.movementType = movementType;
        this.quantityDelta = quantityDelta;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.idempotencyKey = idempotencyKey;
        this.reason = reason;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.reservedQuantityDelta = reservedQuantityDelta;
        this.damagedQuantityDelta = damagedQuantityDelta;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public Warehouse getWarehouse() {
        return warehouse;
    }

    public String getMovementType() {
        return movementType;
    }

    public BigDecimal getQuantityDelta() {
        return quantityDelta;
    }

    public BigDecimal getReservedQuantityDelta() {
        return reservedQuantityDelta;
    }

    public BigDecimal getDamagedQuantityDelta() {
        return damagedQuantityDelta;
    }

    public BigDecimal getQuantityBefore() {
        return quantityBefore;
    }

    public BigDecimal getQuantityAfter() {
        return quantityAfter;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getReason() {
        return reason;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
