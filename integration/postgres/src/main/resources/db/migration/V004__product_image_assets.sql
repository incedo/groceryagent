CREATE TABLE product_image_assets (
    product_id TEXT NOT NULL REFERENCES catalog_products(product_id) ON DELETE CASCADE,
    variant TEXT NOT NULL,
    source_image_id TEXT NOT NULL,
    document JSONB NOT NULL,
    last_global_position BIGINT NOT NULL REFERENCES domain_events(global_position),
    PRIMARY KEY (product_id, variant)
);

CREATE INDEX product_image_assets_source_idx
    ON product_image_assets(source_image_id, variant);
