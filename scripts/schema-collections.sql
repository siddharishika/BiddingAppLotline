-- Collections hold one or more lots. Every lot belongs to a collection.
-- A single-lot posting still has a collection named after the lot.

CREATE TABLE IF NOT EXISTS lot_collections (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(140) NOT NULL,
    seller_id BIGINT NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE auction_items
    ADD COLUMN IF NOT EXISTS collection_id BIGINT REFERENCES lot_collections (id);

CREATE INDEX IF NOT EXISTS idx_lot_collections_seller_id
    ON lot_collections (seller_id);

ALTER TABLE lot_collections
    ADD COLUMN IF NOT EXISTS description VARCHAR(4000);

UPDATE lot_collections
SET description = ''
WHERE description IS NULL;
