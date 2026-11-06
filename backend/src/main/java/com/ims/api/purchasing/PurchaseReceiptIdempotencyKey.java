package com.ims.api.purchasing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "purchase_receipt_idempotency")
public class PurchaseReceiptIdempotencyKey {
    @Id
    @Column(name = "idempotency_key", nullable = false, length = 36, columnDefinition = "char(36)")
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected PurchaseReceiptIdempotencyKey() {
    }

    public PurchaseReceiptIdempotencyKey(String idempotencyKey, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
}
