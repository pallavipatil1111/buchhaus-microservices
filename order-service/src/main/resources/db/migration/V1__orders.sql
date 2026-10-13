-- order-service owns carts and orders. user_id and book_id refer to other services' data, so there
-- are no foreign keys to them; titles and prices are copied into order_items at checkout.
CREATE TABLE cart_items (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id  BIGINT NOT NULL,
    book_id  BIGINT NOT NULL,
    quantity INT    NOT NULL,
    CONSTRAINT uk_cart_user_book UNIQUE (user_id, book_id),
    CONSTRAINT ck_cart_quantity CHECK (quantity BETWEEN 1 AND 10)
);

CREATE TABLE orders (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    customer_email  VARCHAR(160) NOT NULL,
    reservation_id  VARCHAR(36)  NOT NULL,
    stock_confirmed BOOLEAN      NOT NULL DEFAULT FALSE,
    status          VARCHAR(12)  NOT NULL,
    subtotal_paise  BIGINT       NOT NULL,
    shipping_paise  BIGINT       NOT NULL,
    total_paise     BIGINT       NOT NULL,
    ship_name       VARCHAR(80)  NOT NULL,
    ship_phone      VARCHAR(15)  NOT NULL,
    ship_address    VARCHAR(200) NOT NULL,
    ship_city       VARCHAR(60)  NOT NULL,
    ship_pincode    VARCHAR(6)   NOT NULL,
    created_at      TIMESTAMP(6) NOT NULL,
    updated_at      TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_orders_reservation UNIQUE (reservation_id),
    CONSTRAINT ck_orders_total CHECK (total_paise = subtotal_paise + shipping_paise)
);
CREATE INDEX idx_orders_user ON orders (user_id, created_at);
CREATE INDEX idx_orders_status ON orders (status, created_at);
CREATE INDEX idx_orders_unconfirmed ON orders (stock_confirmed, status);

CREATE TABLE order_items (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id         BIGINT       NOT NULL,
    book_id          BIGINT       NOT NULL,
    title_snapshot   VARCHAR(160) NOT NULL,
    unit_price_paise BIGINT       NOT NULL,
    quantity         INT          NOT NULL,
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT ck_items_quantity CHECK (quantity > 0)
);
