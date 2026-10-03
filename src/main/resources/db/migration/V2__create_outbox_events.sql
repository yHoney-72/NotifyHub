CREATE TABLE IF NOT EXISTS outbox_events (
                                             id BIGSERIAL PRIMARY KEY,

                                             aggregate_type VARCHAR(100) NOT NULL,

    aggregate_id BIGINT NOT NULL,

    event_type VARCHAR(100) NOT NULL,

    payload TEXT NOT NULL,

    status VARCHAR(50) NOT NULL,

    attempts INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL,

    published_at TIMESTAMP,

    processing_started_at TIMESTAMP,

    last_error VARCHAR(1000)
    );

CREATE INDEX idx_outbox_status_created_at
    ON outbox_events(status, created_at);

CREATE INDEX idx_outbox_processing_started_at
    ON outbox_events(status, processing_started_at);