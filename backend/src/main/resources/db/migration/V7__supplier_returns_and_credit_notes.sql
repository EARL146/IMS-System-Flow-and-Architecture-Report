CREATE TABLE supplier_returns (
    id BIGINT NOT NULL AUTO_INCREMENT,
    return_number VARCHAR(40) NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    notes VARCHAR(1000) NULL,
    rejection_reason VARCHAR(500) NULL,
    currency_code CHAR(3) NOT NULL,
    expected_credit_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    requested_by_user_id BIGINT NOT NULL,
    reviewed_by_user_id BIGINT NULL,
    dispatched_by_user_id BIGINT NULL,
    cancelled_by_user_id BIGINT NULL,
    requested_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at TIMESTAMP(6) NULL,
    dispatched_at TIMESTAMP(6) NULL,
    cancelled_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_supplier_returns_number UNIQUE (return_number),
    CONSTRAINT uq_supplier_returns_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_supplier_returns_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_supplier_returns_requester FOREIGN KEY (requested_by_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_supplier_returns_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_supplier_returns_dispatcher FOREIGN KEY (dispatched_by_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_supplier_returns_canceller FOREIGN KEY (cancelled_by_user_id) REFERENCES app_users (id),
    CONSTRAINT chk_supplier_returns_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'DISPATCHED', 'CANCELLED')),
    CONSTRAINT chk_supplier_returns_credit CHECK (expected_credit_amount >= 0),
    INDEX ix_supplier_returns_order_time (purchase_order_id, requested_at),
    INDEX ix_supplier_returns_status_time (status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE supplier_return_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    supplier_return_id BIGINT NOT NULL,
    purchase_order_item_id BIGINT NOT NULL,
    line_number SMALLINT UNSIGNED NOT NULL,
    quantity DECIMAL(18,3) NOT NULL,
    damaged_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    unit_cost DECIMAL(19,4) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_supplier_return_po_item UNIQUE (supplier_return_id, purchase_order_item_id),
    CONSTRAINT uq_supplier_return_line UNIQUE (supplier_return_id, line_number),
    CONSTRAINT fk_supplier_return_items_return FOREIGN KEY (supplier_return_id) REFERENCES supplier_returns (id),
    CONSTRAINT fk_supplier_return_items_po_item FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items (id),
    CONSTRAINT chk_supplier_return_items_quantity CHECK (quantity > 0 AND damaged_quantity >= 0 AND damaged_quantity <= quantity),
    CONSTRAINT chk_supplier_return_items_cost CHECK (unit_cost >= 0),
    INDEX ix_supplier_return_items_po_item (purchase_order_item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE supplier_credit_notes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    supplier_return_id BIGINT NOT NULL,
    supplier_reference VARCHAR(100) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    notes VARCHAR(500) NULL,
    recorded_by_user_id BIGINT NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    recorded_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_supplier_credit_notes_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_supplier_credit_notes_return FOREIGN KEY (supplier_return_id) REFERENCES supplier_returns (id),
    CONSTRAINT fk_supplier_credit_notes_recorder FOREIGN KEY (recorded_by_user_id) REFERENCES app_users (id),
    CONSTRAINT chk_supplier_credit_notes_amount CHECK (amount > 0),
    INDEX ix_supplier_credit_notes_return_time (supplier_return_id, recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE supplier_return_idempotency_keys (
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    supplier_return_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (idempotency_key),
    CONSTRAINT uq_supplier_return_idempotency_return UNIQUE (supplier_return_id),
    CONSTRAINT fk_supplier_return_idempotency_return FOREIGN KEY (supplier_return_id) REFERENCES supplier_returns (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE purchase_orders
    ADD COLUMN cancellation_reason VARCHAR(500) NULL AFTER ordered_at,
    ADD COLUMN cancelled_by_user_id BIGINT NULL AFTER cancellation_reason,
    ADD COLUMN cancelled_at TIMESTAMP(6) NULL AFTER cancelled_by_user_id,
    ADD COLUMN cancelled_unreceived_value DECIMAL(19,4) NOT NULL DEFAULT 0 AFTER cancelled_at,
    ADD CONSTRAINT fk_purchase_orders_canceller FOREIGN KEY (cancelled_by_user_id) REFERENCES app_users (id),
    ADD CONSTRAINT chk_purchase_orders_cancelled_value CHECK (cancelled_unreceived_value >= 0);
