-- catalog-service owns books and stock. Money is stored in paise (BIGINT).
CREATE TABLE books (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku            VARCHAR(20)   NOT NULL,
    title          VARCHAR(160)  NOT NULL,
    author         VARCHAR(120)  NOT NULL,
    genre          VARCHAR(40)   NOT NULL,
    description    VARCHAR(2000),
    price_paise    BIGINT        NOT NULL,
    stock          INT           NOT NULL,
    published_year INT,
    pages          INT,
    active         BOOLEAN       NOT NULL DEFAULT TRUE,
    version        BIGINT        NOT NULL DEFAULT 0,
    created_at     TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_books_sku UNIQUE (sku),
    CONSTRAINT ck_books_price CHECK (price_paise >= 0),
    CONSTRAINT ck_books_stock CHECK (stock >= 0)
);
CREATE INDEX idx_books_genre ON books (active, genre);
CREATE INDEX idx_books_title ON books (title);

-- Stock held for a checkout, keyed by an id the order-service chooses (idempotent retries).
CREATE TABLE reservations (
    id         VARCHAR(36)  PRIMARY KEY,
    status     VARCHAR(10)  NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_reservations_status ON reservations (status, created_at);

CREATE TABLE reservation_lines (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id   VARCHAR(36)  NOT NULL,
    book_id          BIGINT       NOT NULL,
    title_snapshot   VARCHAR(160) NOT NULL,
    unit_price_paise BIGINT       NOT NULL,
    quantity         INT          NOT NULL,
    CONSTRAINT fk_lines_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE CASCADE,
    CONSTRAINT ck_lines_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_lines_book ON reservation_lines (book_id);
