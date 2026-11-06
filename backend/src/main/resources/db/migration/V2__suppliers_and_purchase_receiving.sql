CREATE TABLE suppliers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(180) NOT NULL,
    contact_name VARCHAR(140) NULL,
    email VARCHAR(254) NULL,
    phone VARCHAR(40) NULL,
    tax_identifier VARCHAR(80) NULL,
    address_line1 VARCHAR(180) NULL,
    address_line2 VARCHAR(180) NULL,
    city VARCHAR(100) NULL,
    region VARCHAR(100) NULL,
    postal_code VARCHAR(24) NULL,
    country_code CHAR(2) NULL,
    notes TEXT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_suppliers_code UNIQUE (code),
    INDEX ix_suppliers_active_name (active, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_number VARCHAR(40) NOT NULL,
    supplier_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    expected_date DATE NULL,
    notes TEXT NULL,
    rejection_reason VARCHAR(500) NULL,
    created_by_user_id BIGINT NOT NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at TIMESTAMP(6) NULL,
    ordered_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_purchase_orders_number UNIQUE (order_number),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers (id),
    CONSTRAINT fk_purchase_orders_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_purchase_orders_creator FOREIGN KEY (created_by_user_id) REFERENCES app_users (id),
    CONSTRAINT fk_purchase_orders_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES app_users (id),
    INDEX ix_purchase_orders_status_created (status, created_at),
    INDEX ix_purchase_orders_supplier (supplier_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    line_number SMALLINT NOT NULL,
    product_id BIGINT NOT NULL,
    description VARCHAR(180) NOT NULL,
    ordered_quantity DECIMAL(18,3) NOT NULL,
    received_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    unit_cost DECIMAL(19,4) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_purchase_order_line UNIQUE (purchase_order_id, line_number),
    CONSTRAINT fk_purchase_order_items_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_purchase_order_items_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT chk_purchase_order_item_quantity CHECK (ordered_quantity > 0 AND received_quantity >= 0 AND received_quantity <= ordered_quantity),
    CONSTRAINT chk_purchase_order_item_cost CHECK (unit_cost >= 0),
    INDEX ix_purchase_order_items_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_receipt_idempotency (
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE goods_receipts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    delivery_reference VARCHAR(100) NULL,
    received_by_user_id BIGINT NOT NULL,
    received_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    notes VARCHAR(500) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_goods_receipts_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_goods_receipts_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders (id),
    CONSTRAINT fk_goods_receipts_user FOREIGN KEY (received_by_user_id) REFERENCES app_users (id),
    INDEX ix_goods_receipts_order_time (purchase_order_id, received_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE goods_receipt_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    goods_receipt_id BIGINT NOT NULL,
    purchase_order_item_id BIGINT NOT NULL,
    quantity_received DECIMAL(18,3) NOT NULL,
    unit_cost DECIMAL(19,4) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_goods_receipt_order_line UNIQUE (goods_receipt_id, purchase_order_item_id),
    CONSTRAINT fk_goods_receipt_items_receipt FOREIGN KEY (goods_receipt_id) REFERENCES goods_receipts (id),
    CONSTRAINT fk_goods_receipt_items_po_line FOREIGN KEY (purchase_order_item_id) REFERENCES purchase_order_items (id),
    CONSTRAINT chk_goods_receipt_quantity CHECK (quantity_received > 0),
    CONSTRAINT chk_goods_receipt_cost CHECK (unit_cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
