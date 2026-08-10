CREATE TABLE product_previous_ids (
    previous_product_id TEXT PRIMARY KEY,
    product_id TEXT NOT NULL REFERENCES catalog_products(product_id) ON DELETE CASCADE,
    last_global_position BIGINT NOT NULL UNIQUE REFERENCES domain_events(global_position),
    CHECK (previous_product_id <> product_id)
);

CREATE INDEX product_previous_ids_product_idx ON product_previous_ids(product_id);
