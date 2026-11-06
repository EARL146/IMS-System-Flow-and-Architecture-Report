CREATE TABLE roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(96) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO roles (code, name) VALUES
 ('SUPER_ADMIN', 'Super Admin'),
 ('OWNER', 'Business Owner'),
 ('ADMIN', 'Administrator'),
 ('INVENTORY_MANAGER', 'Inventory Manager'),
 ('PURCHASING_OFFICER', 'Purchasing Officer'),
 ('WAREHOUSE_STAFF', 'Warehouse Staff'),
 ('CASHIER', 'Cashier'),
 ('ACCOUNTANT', 'Accountant'),
 ('CUSTOMER_SUPPORT', 'Customer Support'),
 ('CUSTOMER', 'Customer');

CREATE TABLE app_users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_app_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE refresh_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    replaced_by_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_refresh_sessions_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_sessions_user FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_refresh_sessions_replaced FOREIGN KEY (replaced_by_id) REFERENCES refresh_sessions (id),
    CONSTRAINT uq_refresh_sessions_replacement UNIQUE (replaced_by_id),
    INDEX ix_refresh_sessions_user_expiry (user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_categories_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    sku VARCHAR(80) NOT NULL,
    barcode VARCHAR(80) NULL,
    name VARCHAR(180) NOT NULL,
    description TEXT NULL,
    category_id BIGINT NULL,
    unit_of_measure VARCHAR(32) NOT NULL DEFAULT 'each',
    tracking_type VARCHAR(32) NOT NULL DEFAULT 'QUANTITY',
    cost_price DECIMAL(19,4) NOT NULL DEFAULT 0,
    selling_price DECIMAL(19,4) NOT NULL,
    reorder_level DECIMAL(18,3) NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT uq_products_barcode UNIQUE (barcode),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT chk_products_cost CHECK (cost_price >= 0),
    CONSTRAINT chk_products_price CHECK (selling_price >= 0),
    CONSTRAINT chk_products_reorder CHECK (reorder_level >= 0),
    INDEX ix_products_active_name (active, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE warehouses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(48) NOT NULL,
    name VARCHAR(160) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_warehouses_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_balances (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    on_hand_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    reserved_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    damaged_quantity DECIMAL(18,3) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_inventory_balance_product_warehouse UNIQUE (product_id, warehouse_id),
    CONSTRAINT fk_inventory_balance_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_balance_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT chk_inventory_on_hand CHECK (on_hand_quantity >= 0),
    CONSTRAINT chk_inventory_reserved CHECK (reserved_quantity >= 0 AND reserved_quantity <= on_hand_quantity),
    CONSTRAINT chk_inventory_damaged CHECK (damaged_quantity >= 0 AND damaged_quantity <= on_hand_quantity),
    INDEX ix_inventory_warehouse (warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_movements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    warehouse_id BIGINT NOT NULL,
    actor_user_id BIGINT NULL,
    movement_type VARCHAR(32) NOT NULL,
    quantity_delta DECIMAL(18,3) NOT NULL,
    quantity_before DECIMAL(18,3) NOT NULL,
    quantity_after DECIMAL(18,3) NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    reason VARCHAR(240) NOT NULL,
    reference_type VARCHAR(48) NULL,
    reference_id VARCHAR(80) NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_inventory_movement_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_inventory_movement_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_inventory_movement_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_inventory_movement_actor FOREIGN KEY (actor_user_id) REFERENCES app_users (id),
    INDEX ix_inventory_movement_product_time (product_id, occurred_at),
    INDEX ix_inventory_movement_warehouse_time (warehouse_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE inventory_idempotency_keys (
    idempotency_key CHAR(36) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
