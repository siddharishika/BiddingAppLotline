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
    gateway_transaction_id VARCHAR(80),
    last_four VARCHAR(4),
    failure_reason VARCHAR(240),
    created_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE users ADD COLUMN IF NOT EXISTS username VARCHAR(40);
ALTER TABLE users ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS password VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20);
ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled BOOLEAN;
ALTER TABLE users ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS title VARCHAR(140);
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS description VARCHAR(4000);
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS category VARCHAR(60);
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS starting_price NUMERIC(12, 2);
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS min_increment NUMERIC(12, 2);
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS current_price NUMERIC(12, 2);
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS seller_id BIGINT;
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS winner_id BIGINT;
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS start_time TIMESTAMPTZ;
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS end_time TIMESTAMPTZ;
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE auction_items ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

ALTER TABLE bids ADD COLUMN IF NOT EXISTS auction_id BIGINT;
ALTER TABLE bids ADD COLUMN IF NOT EXISTS bidder_id BIGINT;
ALTER TABLE bids ADD COLUMN IF NOT EXISTS amount NUMERIC(12, 2);
ALTER TABLE bids ADD COLUMN IF NOT EXISTS placed_at TIMESTAMPTZ;

ALTER TABLE payments ADD COLUMN IF NOT EXISTS auction_id BIGINT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS payer_id BIGINT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS amount NUMERIC(12, 2);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS status VARCHAR(20);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS gateway_transaction_id VARCHAR(80);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS last_four VARCHAR(4);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(240);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_auction_items_status_start_time
    ON auction_items (status, start_time);

CREATE INDEX IF NOT EXISTS idx_auction_items_status_end_time
    ON auction_items (status, end_time);

CREATE INDEX IF NOT EXISTS idx_auction_items_seller_id
    ON auction_items (seller_id);

CREATE INDEX IF NOT EXISTS idx_auction_items_winner_id
    ON auction_items (winner_id);

CREATE INDEX IF NOT EXISTS idx_bids_auction_id
    ON bids (auction_id);

CREATE INDEX IF NOT EXISTS idx_bids_auction_bidder
    ON bids (auction_id, bidder_id);

CREATE INDEX IF NOT EXISTS idx_payments_auction_id
    ON payments (auction_id);

CREATE INDEX IF NOT EXISTS idx_payments_payer_id
    ON payments (payer_id);

CREATE TABLE IF NOT EXISTS lot_collections (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(140) NOT NULL,
    seller_id BIGINT NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL
);
-- Every lot belongs to a collection. A single-lot posting still has a collection row.

ALTER TABLE auction_items
    ADD COLUMN IF NOT EXISTS collection_id BIGINT REFERENCES lot_collections (id);

CREATE INDEX IF NOT EXISTS idx_lot_collections_seller_id
    ON lot_collections (seller_id);

CREATE INDEX IF NOT EXISTS idx_auction_items_collection_id
    ON auction_items (collection_id);

ALTER TABLE lot_collections
    ADD COLUMN IF NOT EXISTS description VARCHAR(4000);
