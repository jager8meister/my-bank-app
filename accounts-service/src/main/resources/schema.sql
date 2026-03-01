
CREATE SCHEMA IF NOT EXISTS accounts_schema;


SET search_path TO accounts_schema;


CREATE TABLE IF NOT EXISTS accounts_schema.accounts (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    login VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    birthdate DATE NOT NULL,
    balance BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_accounts_login ON accounts_schema.accounts(login);

CREATE TABLE IF NOT EXISTS accounts_schema.outbox_events (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outbox_processed ON accounts_schema.outbox_events(processed) WHERE processed = false;
