ALTER TABLE inventory_movements
    ADD COLUMN damaged_quantity_delta DECIMAL(18,3) NOT NULL DEFAULT 0 AFTER reserved_quantity_delta;

ALTER TABLE pos_sales
    ADD COLUMN payment_status VARCHAR(32) NOT NULL DEFAULT 'PAID' AFTER payment_method,
    ADD CONSTRAINT chk_pos_sales_payment_status
        CHECK (payment_status IN ('PAID', 'PARTIALLY_REFUNDED', 'REFUNDED'));

CREATE TABLE return_cases (
    id BIGINT NOT NULL AUTO_INCREMENT,
    return_number VARCHAR(40) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    customer_order_id BIGINT NULL,
    pos_sale_id BIGINT NULL,
    customer_user_id BIGINT NULL,
    requested_by_user_id BIGINT NOT NULL,
    reviewed_by_user_id BIGINT NULL,
    inspected_by_user_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    notes VARCHAR(1000) NULL,
    rejection_reason VARCHAR(500) NULL,
    currency_code CHAR(3) NOT NULL,
    refund_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    idempotency_key CHAR(36) NOT NULL,
    requested_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    reviewed_at TIMESTAMP(6) NULL,
    inspected_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_return_cases_number UNIQUE (return_number),
    CONSTRAINT uq_return_cases_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_return_cases_order FOREIGN KEY (customer_order_id) REFERENCES customer_orders (id),
    CONSTRAINT fk_return_cases_pos_sale FOREIGN KEY (pos_sale_id) REFERENCES pos_sales (id),
    CONSTRAINT fk_return_cases_customer FOREIGN KEY (customer_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_return_cases_requested_by FOREIGN KEY (requested_by_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_return_cases_reviewed_by FOREIGN KEY (reviewed_by_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_return_cases_inspected_by FOREIGN KEY (inspected_by_user_id) REFERENCES app_users (id),
    CONSTRAINT chk_return_cases_source CHECK (
        (source_type = 'CUSTOMER_ORDER' AND customer_order_id IS NOT NULL AND pos_sale_id IS NULL)
        OR (source_type = 'POS_SALE' AND pos_sale_id IS NOT NULL AND customer_order_id IS NULL)
    ),
    CONSTRAINT chk_return_cases_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'INSPECTED', 'REFUNDED')),
    CONSTRAINT chk_return_cases_refund CHECK (refund_amount >= 0),
    INDEX ix_return_cases_order (customer_order_id, requested_at),
    INDEX ix_return_cases_pos_sale (pos_sale_id, requested_at),
    INDEX ix_return_cases_customer (customer_user_id, requested_at),
    INDEX ix_return_cases_status (status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE return_case_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    return_case_id BIGINT NOT NULL,
    customer_order_item_id BIGINT NULL,
    pos_sale_item_id BIGINT NULL,
    product_id BIGINT NOT NULL,
    line_number SMALLINT UNSIGNED NOT NULL,
    sku_snapshot VARCHAR(80) NOT NULL,
    product_name_snapshot VARCHAR(180) NOT NULL,
    unit_of_measure_snapshot VARCHAR(32) NOT NULL,
    unit_price DECIMAL(19,4) NOT NULL,
    requested_quantity DECIMAL(18,3) NOT NULL,
    approved_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    restock_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    damaged_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    scrap_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_return_case_item_line UNIQUE (return_case_id, line_number),
    CONSTRAINT fk_return_case_items_case FOREIGN KEY (return_case_id) REFERENCES return_cases (id),
    CONSTRAINT fk_return_case_items_order_item FOREIGN KEY (customer_order_item_id) REFERENCES customer_order_items (id),
    CONSTRAINT fk_return_case_items_pos_item FOREIGN KEY (pos_sale_item_id) REFERENCES pos_sale_items (id),
    CONSTRAINT fk_return_case_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_return_case_items_source CHECK (
        (customer_order_item_id IS NOT NULL AND pos_sale_item_id IS NULL)
        OR (pos_sale_item_id IS NOT NULL AND customer_order_item_id IS NULL)
    ),
    CONSTRAINT chk_return_case_items_quantity CHECK (
        requested_quantity > 0 AND approved_quantity >= 0 AND approved_quantity <= requested_quantity
        AND restock_quantity >= 0 AND damaged_quantity >= 0 AND scrap_quantity >= 0
        AND restock_quantity + damaged_quantity + scrap_quantity <= approved_quantity
    ),
    CONSTRAINT chk_return_case_items_price CHECK (unit_price >= 0),
    INDEX ix_return_case_items_order_item (customer_order_item_id),
    INDEX ix_return_case_items_pos_item (pos_sale_item_id),
    INDEX ix_return_case_items_product (product_id, return_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE return_refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    return_case_id BIGINT NOT NULL,
    refund_number VARCHAR(40) NOT NULL,
    refund_method VARCHAR(32) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    reference VARCHAR(100) NULL,
    notes VARCHAR(500) NULL,
    recorded_by_user_id BIGINT NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_return_refunds_case UNIQUE (return_case_id),
    CONSTRAINT uq_return_refunds_number UNIQUE (refund_number),
    CONSTRAINT uq_return_refunds_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_return_refunds_case FOREIGN KEY (return_case_id) REFERENCES return_cases (id),
    CONSTRAINT fk_return_refunds_recorder FOREIGN KEY (recorded_by_user_id) REFERENCES app_users (id),
    CONSTRAINT chk_return_refunds_amount CHECK (amount > 0),
    CONSTRAINT chk_return_refunds_method CHECK (refund_method IN ('CASH', 'MANUAL_TRANSFER', 'EXTERNAL_TERMINAL')),
    CONSTRAINT chk_return_refunds_reference CHECK (
        (refund_method = 'CASH' AND reference IS NULL)
        OR (refund_method IN ('MANUAL_TRANSFER', 'EXTERNAL_TERMINAL') AND reference IS NOT NULL)
    ),
    INDEX ix_return_refunds_created (created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE return_request_idempotency_keys (
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    return_case_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (idempotency_key),
    CONSTRAINT uq_return_request_key_case UNIQUE (return_case_id),
    CONSTRAINT fk_return_request_key_case FOREIGN KEY (return_case_id) REFERENCES return_cases (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE return_refund_idempotency_keys (
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    return_refund_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (idempotency_key),
    CONSTRAINT uq_return_refund_key_refund UNIQUE (return_refund_id),
    CONSTRAINT fk_return_refund_key_refund FOREIGN KEY (return_refund_id) REFERENCES return_refunds (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
