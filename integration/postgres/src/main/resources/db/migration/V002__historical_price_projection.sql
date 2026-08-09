CREATE TABLE product_price_history (
    observation_id TEXT PRIMARY KEY,
    product_id TEXT NOT NULL,
    retailer_id TEXT NOT NULL,
    purchased_at TIMESTAMPTZ NOT NULL,
    document JSONB NOT NULL,
    last_global_position BIGINT NOT NULL UNIQUE REFERENCES domain_events(global_position)
);

CREATE INDEX product_price_history_product_time_idx
    ON product_price_history(product_id, purchased_at, observation_id);
