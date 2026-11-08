package com.ims.api.returns;

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
@Table(name = "return_refund_idempotency_keys")
public class ReturnRefundIdempotencyKey {
    @Id
    @Column(name = "idempotency_key", nullable = false, length = 36, columnDefinition = "char(36)")
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_refund_id", unique = true)
    private ReturnRefund refund;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ReturnRefundIdempotencyKey() {
    }

    public ReturnRefundIdempotencyKey(String idempotencyKey, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public void setRefund(ReturnRefund refund) { this.refund = refund; }
    public String getRequestHash() { return requestHash; }
    public ReturnRefund getRefund() { return refund; }
}
