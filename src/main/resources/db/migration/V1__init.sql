CREATE TABLE stores (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    cnpj               VARCHAR(14)  NOT NULL,
    state_registration VARCHAR(14)  NOT NULL,
    uf                 VARCHAR(2)   NOT NULL,
    ibge_city_code     VARCHAR(7)   NOT NULL,
    city               VARCHAR(60)  NOT NULL,
    csc_id             VARCHAR(6)   NOT NULL,
    csc                VARCHAR(36)  NOT NULL,
    nfce_series        INT          NOT NULL,
    next_nfce_number   BIGINT       NOT NULL,
    version            BIGINT       NOT NULL
);

CREATE TABLE products (
    id        UUID PRIMARY KEY,
    store_id  UUID           NOT NULL REFERENCES stores (id),
    sku       VARCHAR(40)    NOT NULL,
    name      VARCHAR(120)   NOT NULL,
    gtin      VARCHAR(14),
    ncm       VARCHAR(8)     NOT NULL,
    cfop      VARCHAR(4)     NOT NULL,
    csosn     VARCHAR(3)     NOT NULL,
    unit      VARCHAR(6)     NOT NULL,
    price     NUMERIC(15, 2) NOT NULL,
    stock     NUMERIC(15, 3) NOT NULL,
    active    BOOLEAN        NOT NULL,
    version   BIGINT         NOT NULL,
    CONSTRAINT uk_products_sku UNIQUE (store_id, sku)
);

CREATE TABLE cash_sessions (
    id            UUID PRIMARY KEY,
    store_id      UUID           NOT NULL REFERENCES stores (id),
    operator      VARCHAR(60)    NOT NULL,
    status        VARCHAR(10)    NOT NULL,
    opening_float NUMERIC(15, 2) NOT NULL,
    opened_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at     TIMESTAMP WITH TIME ZONE,
    version       BIGINT         NOT NULL
);

CREATE INDEX idx_cash_sessions_open ON cash_sessions (store_id, operator, status);

CREATE TABLE cash_movements (
    session_id  UUID           NOT NULL REFERENCES cash_sessions (id),
    position    INT            NOT NULL,
    type        VARCHAR(20)    NOT NULL,
    method      VARCHAR(20)    NOT NULL,
    amount      NUMERIC(15, 2) NOT NULL,
    note        VARCHAR(120),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (session_id, position)
);

CREATE TABLE cash_closing_lines (
    session_id UUID           NOT NULL REFERENCES cash_sessions (id),
    position   INT            NOT NULL,
    method     VARCHAR(20)    NOT NULL,
    expected   NUMERIC(15, 2) NOT NULL,
    counted    NUMERIC(15, 2) NOT NULL,
    PRIMARY KEY (session_id, position)
);

CREATE TABLE sales (
    id              UUID PRIMARY KEY,
    store_id        UUID           NOT NULL REFERENCES stores (id),
    cash_session_id UUID           NOT NULL REFERENCES cash_sessions (id),
    operator        VARCHAR(60)    NOT NULL,
    total           NUMERIC(15, 2) NOT NULL,
    change_amount   NUMERIC(15, 2) NOT NULL,
    status          VARCHAR(12)    NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_sales_store_created ON sales (store_id, created_at);

CREATE TABLE sale_items (
    sale_id     UUID           NOT NULL REFERENCES sales (id),
    position    INT            NOT NULL,
    product_id  UUID           NOT NULL,
    sku         VARCHAR(40)    NOT NULL,
    description VARCHAR(120)   NOT NULL,
    ncm         VARCHAR(8)     NOT NULL,
    cfop        VARCHAR(4)     NOT NULL,
    csosn       VARCHAR(3)     NOT NULL,
    unit        VARCHAR(6)     NOT NULL,
    quantity    NUMERIC(15, 3) NOT NULL,
    unit_price  NUMERIC(15, 2) NOT NULL,
    total       NUMERIC(15, 2) NOT NULL,
    PRIMARY KEY (sale_id, position)
);

CREATE TABLE sale_payments (
    sale_id  UUID           NOT NULL REFERENCES sales (id),
    position INT            NOT NULL,
    method   VARCHAR(20)    NOT NULL,
    amount   NUMERIC(15, 2) NOT NULL,
    PRIMARY KEY (sale_id, position)
);

CREATE TABLE fiscal_documents (
    id                    UUID PRIMARY KEY,
    store_id              UUID         NOT NULL REFERENCES stores (id),
    sale_id               UUID         NOT NULL UNIQUE REFERENCES sales (id),
    number                BIGINT       NOT NULL,
    series                INT          NOT NULL,
    access_key            VARCHAR(44)  NOT NULL UNIQUE,
    emission_type         VARCHAR(24)  NOT NULL,
    status                VARCHAR(24)  NOT NULL,
    xml                   TEXT         NOT NULL,
    qr_code_url           VARCHAR(600) NOT NULL,
    protocol              VARCHAR(20),
    status_reason         VARCHAR(255),
    issued_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    authorized_at         TIMESTAMP WITH TIME ZONE,
    transmission_attempts INT          NOT NULL,
    version               BIGINT       NOT NULL,
    CONSTRAINT uk_fiscal_number UNIQUE (store_id, series, number)
);

CREATE INDEX idx_fiscal_status ON fiscal_documents (status, issued_at);

CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    store_id    UUID          NOT NULL,
    username    VARCHAR(60)   NOT NULL,
    action      VARCHAR(60)   NOT NULL,
    entity_id   VARCHAR(60),
    details     VARCHAR(1000),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_audit_store_time ON audit_log (store_id, occurred_at);
