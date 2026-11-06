package com.ims.api.purchasing;

public enum PurchaseOrderStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED
}
