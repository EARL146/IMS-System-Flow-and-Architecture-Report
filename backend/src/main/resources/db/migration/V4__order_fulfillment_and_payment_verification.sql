CREATE TABLE customer_order_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_order_id BIGINT NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    changed_by_user_id BIGINT NOT NULL,
    note VARCHAR(500) NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_customer_order_history_order FOREIGN KEY (customer_order_id) REFERENCES customer_orders (id),
    CONSTRAINT fk_customer_order_history_actor FOREIGN KEY (changed_by_user_id) REFERENCES app_users (id),
    INDEX ix_customer_order_history_order (customer_order_id, occurred_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO customer_order_status_history
    (customer_order_id, from_status, to_status, changed_by_user_id, note, occurred_at)
SELECT id, NULL, status, customer_user_id, 'State captured when order history was introduced', created_at
FROM customer_orders;

CREATE TABLE customer_payment_verifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    customer_order_id BIGINT NOT NULL,
    verified_by_user_id BIGINT NOT NULL,
    payment_method VARCHAR(40) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    reference VARCHAR(100) NULL,
    notes VARCHAR(500) NULL,
    idempotency_key CHAR(36) NOT NULL,
    verified_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_customer_payment_order UNIQUE (customer_order_id),
    CONSTRAINT uq_customer_payment_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_customer_payment_order FOREIGN KEY (customer_order_id) REFERENCES customer_orders (id),
    CONSTRAINT fk_customer_payment_verifier FOREIGN KEY (verified_by_user_id) REFERENCES app_users (id),
    CONSTRAINT chk_customer_payment_amount CHECK (amount > 0),
    INDEX ix_customer_payment_verifier_time (verified_by_user_id, verified_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
