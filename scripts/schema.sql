-- Lotline PostgreSQL schema (idempotent).
-- Upcoming lots use auction_items.status + start_time.
-- Unique bidder counts are queried from bids, not stored as a column.

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(40) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS lot_collections (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(140) NOT NULL,
    description VARCHAR(4000) NOT NULL DEFAULT '',
    seller_id BIGINT NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS auction_items (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(140) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    category VARCHAR(60) NOT NULL,
    image_url VARCHAR(500),
    starting_price NUMERIC(12, 2) NOT NULL,
    min_increment NUMERIC(12, 2) NOT NULL,
    current_price NUMERIC(12, 2) NOT NULL,
    seller_id BIGINT NOT NULL REFERENCES users (id),
    winner_id BIGINT REFERENCES users (id),
    collection_id BIGINT REFERENCES lot_collections (id),
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS bids (
    id BIGSERIAL PRIMARY KEY,
    auction_id BIGINT NOT NULL REFERENCES auction_items (id),
    bidder_id BIGINT NOT NULL REFERENCES users (id),
    amount NUMERIC(12, 2) NOT NULL,
    placed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS payments (
    id BIGSERIAL PRIMARY KEY,
    auction_id BIGINT NOT NULL REFERENCES auction_items (id),
    payer_id BIGINT NOT NULL REFERENCES users (id),
    amount NUMERIC(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    gateway_transaction_id VARCHAR(255),
    checkout_session_id VARCHAR(255),
    last_four VARCHAR(4),
    failure_reason VARCHAR(240),
    created_at TIMESTAMPTZ NOT NULL
);

-- Existing databases created before collections / hosted checkout.
ALTER TABLE lot_collections
    ADD COLUMN IF NOT EXISTS description VARCHAR(4000);

UPDATE lot_collections
SET description = ''
WHERE description IS NULL;

ALTER TABLE auction_items
    ADD COLUMN IF NOT EXISTS collection_id BIGINT REFERENCES lot_collections (id);

ALTER TABLE payments ADD COLUMN IF NOT EXISTS checkout_session_id VARCHAR(255);
ALTER TABLE payments ALTER COLUMN gateway_transaction_id TYPE VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS idx_payments_checkout_session_id
    ON payments (checkout_session_id)
    WHERE checkout_session_id IS NOT NULL;

ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check;
ALTER TABLE payments ADD CONSTRAINT payments_status_check
    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'EXPIRED'));

CREATE INDEX IF NOT EXISTS idx_auction_items_status_start_time
    ON auction_items (status, start_time);

CREATE INDEX IF NOT EXISTS idx_auction_items_status_end_time
    ON auction_items (status, end_time);

CREATE INDEX IF NOT EXISTS idx_auction_items_seller_id
    ON auction_items (seller_id);

CREATE INDEX IF NOT EXISTS idx_auction_items_winner_id
    ON auction_items (winner_id);

CREATE INDEX IF NOT EXISTS idx_auction_items_collection_id
    ON auction_items (collection_id);

CREATE INDEX IF NOT EXISTS idx_lot_collections_seller_id
    ON lot_collections (seller_id);

CREATE INDEX IF NOT EXISTS idx_bids_auction_id
    ON bids (auction_id);

CREATE INDEX IF NOT EXISTS idx_bids_auction_bidder
    ON bids (auction_id, bidder_id);

CREATE INDEX IF NOT EXISTS idx_payments_auction_id
    ON payments (auction_id);

CREATE INDEX IF NOT EXISTS idx_payments_payer_id
    ON payments (payer_id);
