package com.ims.api.pos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "pos_sale_idempotency_keys")
public class PosSaleIdempotencyKey {
    @Id
    @Column(name = "idempotency_key", nullable = false, length = 36, columnDefinition = "char(36)")
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pos_sale_id", unique = true)
    private PosSale sale;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected PosSaleIdempotencyKey() {
    }

    public PosSaleIdempotencyKey(String idempotencyKey, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public void setSale(PosSale sale) { this.sale = sale; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public PosSale getSale() { return sale; }
    public Instant getCreatedAt() { return createdAt; }
}
