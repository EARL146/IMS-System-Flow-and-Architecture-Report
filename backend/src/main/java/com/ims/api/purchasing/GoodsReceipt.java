package com.ims.api.purchasing;

import com.ims.api.auth.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "goods_receipts")
public class GoodsReceipt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "delivery_reference", length = 100)
    private String deliveryReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "received_by_user_id", nullable = false)
    private AppUser receivedBy;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<GoodsReceiptItem> items = new ArrayList<>();

    protected GoodsReceipt() {
    }

    public GoodsReceipt(PurchaseOrder purchaseOrder, String idempotencyKey, String requestHash,
                        String deliveryReference, AppUser receivedBy, String notes) {
        this.purchaseOrder = purchaseOrder;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.deliveryReference = deliveryReference;
        this.receivedBy = receivedBy;
        this.notes = notes;
    }

    public void addItem(GoodsReceiptItem item) {
        item.setReceipt(this);
        items.add(item);
    }

    public Long getId() { return id; }
    public PurchaseOrder getPurchaseOrder() { return purchaseOrder; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public String getDeliveryReference() { return deliveryReference; }
    public AppUser getReceivedBy() { return receivedBy; }
    public Instant getReceivedAt() { return receivedAt; }
    public String getNotes() { return notes; }
    public List<GoodsReceiptItem> getItems() { return items; }
}
