CREATE TABLE event_streams (
    stream_id TEXT PRIMARY KEY,
    current_version BIGINT NOT NULL CHECK (current_version >= 0)
);

CREATE TABLE event_commands (
    command_id UUID PRIMARY KEY,
    stream_id TEXT NOT NULL,
    stream_version BIGINT,
    first_global_position BIGINT,
    last_global_position BIGINT,
    event_count INTEGER,
    CHECK (event_count IS NULL OR event_count > 0)
);

CREATE TABLE domain_events (
    global_position BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    stream_id TEXT NOT NULL REFERENCES event_streams(stream_id),
    stream_version BIGINT NOT NULL CHECK (stream_version > 0),
    event_type TEXT NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    producer_id TEXT NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    payload JSONB NOT NULL,
    UNIQUE (stream_id, stream_version)
);

CREATE INDEX domain_events_stream_position_idx
    ON domain_events(stream_id, stream_version);

CREATE FUNCTION reject_domain_event_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'domain_events is append-only';
END;
$$;

CREATE TRIGGER domain_events_reject_update_delete
    BEFORE UPDATE OR DELETE ON domain_events
    FOR EACH STATEMENT EXECUTE FUNCTION reject_domain_event_mutation();

CREATE TRIGGER domain_events_reject_truncate
    BEFORE TRUNCATE ON domain_events
    FOR EACH STATEMENT EXECUTE FUNCTION reject_domain_event_mutation();

CREATE TABLE catalog_products (
    product_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    brand TEXT,
    document JSONB NOT NULL,
    last_global_position BIGINT NOT NULL REFERENCES domain_events(global_position)
);

CREATE INDEX catalog_products_name_idx ON catalog_products(lower(name));
