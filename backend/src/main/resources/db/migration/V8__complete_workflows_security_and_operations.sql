ALTER TABLE customer_orders
    ADD COLUMN fulfillment_type VARCHAR(32) NOT NULL DEFAULT 'DELIVERY' AFTER warehouse_id,
    ADD CONSTRAINT chk_customer_orders_fulfillment_type CHECK (fulfillment_type IN ('DELIVERY', 'STORE_PICKUP'));

ALTER TABLE app_users
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0 AFTER enabled,
    ADD COLUMN locked_until TIMESTAMP(6) NULL AFTER failed_login_attempts;

CREATE TABLE user_warehouse_scopes (
    user_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, warehouse_id),
    CONSTRAINT fk_user_warehouse_scopes_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_user_warehouse_scopes_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE password_reset_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    used_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    INDEX ix_password_reset_tokens_user_expiry (user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE security_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(64) NOT NULL,
    actor_user_id BIGINT NULL,
    actor_email VARCHAR(254) NULL,
    target_type VARCHAR(64) NULL,
    target_id VARCHAR(80) NULL,
    summary VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_security_audit_events_actor FOREIGN KEY (actor_user_id) REFERENCES app_users (id),
    INDEX ix_security_audit_events_time (occurred_at, id),
    INDEX ix_security_audit_events_type_time (event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE supplier_payables (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payable_number VARCHAR(40) NOT NULL,
    purchase_order_id BIGINT NOT NULL,
    supplier_id BIGINT NOT NULL,
    invoice_reference VARCHAR(100) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    gross_amount DECIMAL(19,4) NOT NULL,
    credit_applied_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    paid_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    due_date DATE NULL,
    notes VARCHAR(500) NULL,
    created_by_user_id BIGINT NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_supplier_payables_number UNIQUE (payable_number),
    CONSTRAINT uq_supplier_payables_po UNIQUE (purchase_order_id),
    CONSTRAINT uq_supplier_payables_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_supplier_payables_po FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_supplier_payables_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_supplier_payables_creator FOREIGN KEY (created_by_user_id) REFERENCES app_users (id),
    CONSTRAINT chk_supplier_payables_status CHECK (status IN ('OPEN', 'PARTIALLY_PAID', 'SETTLED', 'CANCELLED')),
    CONSTRAINT chk_supplier_payables_amounts CHECK (
        gross_amount >= 0 AND credit_applied_amount >= 0 AND paid_amount >= 0
    ),
    INDEX ix_supplier_payables_supplier_status (supplier_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE supplier_payable_payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    supplier_payable_id BIGINT NOT NULL,
    payment_number VARCHAR(40) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    reference VARCHAR(100) NULL,
    notes VARCHAR(500) NULL,
    recorded_by_user_id BIGINT NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    paid_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_supplier_payable_payments_number UNIQUE (payment_number),
    CONSTRAINT uq_supplier_payable_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_supplier_payable_payments_payable FOREIGN KEY (supplier_payable_id) REFERENCES supplier_payables (id),
    CONSTRAINT fk_supplier_payable_payments_recorder FOREIGN KEY (recorded_by_user_id) REFERENCES app_users (id),
    CONSTRAINT chk_supplier_payable_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_supplier_payable_payments_method CHECK (payment_method IN ('BANK_TRANSFER', 'CHECK', 'CASH')),
    INDEX ix_supplier_payable_payments_payable_time (supplier_payable_id, paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE support_tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ticket_number VARCHAR(40) NOT NULL,
    customer_user_id BIGINT NOT NULL,
    customer_order_id BIGINT NULL,
    subject VARCHAR(200) NOT NULL,
    category VARCHAR(48) NOT NULL,
    priority VARCHAR(24) NOT NULL,
    status VARCHAR(32) NOT NULL,
    assigned_staff_user_id BIGINT NULL,
    idempotency_key CHAR(36) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_support_tickets_number UNIQUE (ticket_number),
    CONSTRAINT uq_support_tickets_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_support_tickets_customer FOREIGN KEY (customer_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_support_tickets_order FOREIGN KEY (customer_order_id) REFERENCES customer_orders (id),
    CONSTRAINT fk_support_tickets_assignee FOREIGN KEY (assigned_staff_user_id) REFERENCES app_users (id),
    CONSTRAINT chk_support_tickets_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_support_tickets_priority CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    INDEX ix_support_tickets_customer_time (customer_user_id, created_at),
    INDEX ix_support_tickets_status_time (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE support_ticket_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    support_ticket_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    author_role VARCHAR(40) NOT NULL,
    message_body VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_support_ticket_messages_ticket FOREIGN KEY (support_ticket_id) REFERENCES support_tickets (id),
    CONSTRAINT fk_support_ticket_messages_author FOREIGN KEY (author_user_id) REFERENCES app_users (id),
    INDEX ix_support_ticket_messages_ticket_time (support_ticket_id, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    notification_type VARCHAR(48) NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(500) NOT NULL,
    reference_type VARCHAR(48) NULL,
    reference_id VARCHAR(80) NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    read_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_user_notifications_recipient FOREIGN KEY (recipient_user_id) REFERENCES app_users (id),
    INDEX ix_user_notifications_recipient_time (recipient_user_id, is_read, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
