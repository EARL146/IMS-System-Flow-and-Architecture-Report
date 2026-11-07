ALTER TABLE inventory_movements
    ADD COLUMN reserved_quantity_delta DECIMAL(18,3) NOT NULL DEFAULT 0 AFTER quantity_delta;

CREATE TABLE customer_addresses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    label VARCHAR(80) NOT NULL,
    recipient_name VARCHAR(160) NOT NULL,
    phone VARCHAR(40) NOT NULL,
    address_line1 VARCHAR(180) NOT NULL,
    address_line2 VARCHAR(180) NULL,
    city VARCHAR(100) NOT NULL,
    region VARCHAR(100) NULL,
    postal_code VARCHAR(24) NULL,
    country_code CHAR(2) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_customer_addresses_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    INDEX ix_customer_addresses_user_default (user_id, is_default, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shopping_cart_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(18,3) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_cart_user_product UNIQUE (user_id, product_id),
    CONSTRAINT fk_cart_items_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_cart_item_quantity CHECK (quantity > 0),
    INDEX ix_cart_items_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE customer_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_number VARCHAR(40) NOT NULL,
    customer_user_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    payment_method VARCHAR(40) NOT NULL,
    payment_status VARCHAR(40) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    subtotal DECIMAL(19,4) NOT NULL,
    tax_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    shipping_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    discount_amount DECIMAL(19,4) NOT NULL DEFAULT 0,
    total DECIMAL(19,4) NOT NULL,
    address_label VARCHAR(80) NOT NULL,
    recipient_name VARCHAR(160) NOT NULL,
    recipient_phone VARCHAR(40) NOT NULL,
    delivery_address_line1 VARCHAR(180) NOT NULL,
    delivery_address_line2 VARCHAR(180) NULL,
    delivery_city VARCHAR(100) NOT NULL,
    delivery_region VARCHAR(100) NULL,
    delivery_postal_code VARCHAR(24) NULL,
    delivery_country_code CHAR(2) NOT NULL,
    notes VARCHAR(1000) NULL,
    cancelled_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_customer_orders_number UNIQUE (order_number),
    CONSTRAINT fk_customer_orders_user FOREIGN KEY (customer_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_customer_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT chk_customer_order_amounts CHECK (
        subtotal >= 0 AND tax_amount >= 0 AND shipping_amount >= 0
        AND discount_amount >= 0 AND total >= 0
    ),
    INDEX ix_customer_orders_user_created (customer_user_id, created_at),
    INDEX ix_customer_orders_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE customer_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_order_id BIGINT NOT NULL,
    line_number SMALLINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_snapshot VARCHAR(80) NOT NULL,
    product_name_snapshot VARCHAR(180) NOT NULL,
    unit_of_measure_snapshot VARCHAR(32) NOT NULL,
    quantity DECIMAL(18,3) NOT NULL,
    unit_price DECIMAL(19,4) NOT NULL,
    line_total DECIMAL(19,4) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_customer_order_line UNIQUE (customer_order_id, line_number),
    CONSTRAINT fk_customer_order_items_order FOREIGN KEY (customer_order_id) REFERENCES customer_orders (id),
    CONSTRAINT fk_customer_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_customer_order_item_values CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0),
    INDEX ix_customer_order_items_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE checkout_idempotency_keys (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    customer_order_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_checkout_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT uq_checkout_order UNIQUE (customer_order_id),
    CONSTRAINT fk_checkout_keys_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_checkout_keys_order FOREIGN KEY (customer_order_id) REFERENCES customer_orders (id),
    INDEX ix_checkout_keys_order (customer_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
