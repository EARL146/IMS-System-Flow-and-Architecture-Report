CREATE TABLE pos_sales (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sale_number VARCHAR(40) NOT NULL,
    cashier_user_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    customer_user_id BIGINT NULL,
    payment_method VARCHAR(32) NOT NULL,
    payment_reference VARCHAR(100) NULL,
    currency_code CHAR(3) NOT NULL,
    subtotal DECIMAL(19,4) NOT NULL,
    discount_amount DECIMAL(19,4) NOT NULL,
    tax_amount DECIMAL(19,4) NOT NULL,
    total DECIMAL(19,4) NOT NULL,
    amount_tendered DECIMAL(19,4) NULL,
    change_given DECIMAL(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_pos_sales_number UNIQUE (sale_number),
    CONSTRAINT fk_pos_sales_cashier FOREIGN KEY (cashier_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_pos_sales_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_pos_sales_customer FOREIGN KEY (customer_user_id) REFERENCES app_users (id),
    CONSTRAINT chk_pos_sales_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_pos_sales_discount CHECK (discount_amount >= 0 AND discount_amount < subtotal),
    CONSTRAINT chk_pos_sales_tax CHECK (tax_amount >= 0),
    CONSTRAINT chk_pos_sales_total CHECK (total > 0),
    CONSTRAINT chk_pos_sales_tender CHECK (
        (payment_method = 'CASH' AND amount_tendered IS NOT NULL AND amount_tendered >= total
            AND payment_reference IS NULL AND change_given = amount_tendered - total)
        OR (payment_method = 'CARD_TERMINAL' AND amount_tendered IS NULL
            AND payment_reference IS NOT NULL AND change_given = 0)
    ),
    INDEX ix_pos_sales_created (created_at, id),
    INDEX ix_pos_sales_cashier_created (cashier_user_id, created_at),
    INDEX ix_pos_sales_warehouse_created (warehouse_id, created_at),
    INDEX ix_pos_sales_customer_created (customer_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pos_sale_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    pos_sale_id BIGINT NOT NULL,
    line_number SMALLINT UNSIGNED NOT NULL,
    product_id BIGINT NOT NULL,
    sku_snapshot VARCHAR(80) NOT NULL,
    product_name_snapshot VARCHAR(180) NOT NULL,
    unit_of_measure_snapshot VARCHAR(32) NOT NULL,
    quantity DECIMAL(18,3) NOT NULL,
    unit_price DECIMAL(19,4) NOT NULL,
    unit_cost DECIMAL(19,4) NOT NULL,
    line_total DECIMAL(19,4) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_pos_sale_item_line UNIQUE (pos_sale_id, line_number),
    CONSTRAINT fk_pos_sale_items_sale FOREIGN KEY (pos_sale_id) REFERENCES pos_sales (id),
    CONSTRAINT fk_pos_sale_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_pos_sale_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_pos_sale_item_price CHECK (unit_price >= 0),
    CONSTRAINT chk_pos_sale_item_cost CHECK (unit_cost >= 0),
    CONSTRAINT chk_pos_sale_item_total CHECK (line_total >= 0),
    INDEX ix_pos_sale_items_product (product_id, pos_sale_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE pos_sale_idempotency_keys (
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    pos_sale_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (idempotency_key),
    CONSTRAINT uq_pos_sale_key_sale UNIQUE (pos_sale_id),
    CONSTRAINT fk_pos_sale_key_sale FOREIGN KEY (pos_sale_id) REFERENCES pos_sales (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
