
CREATE SCHEMA IF NOT EXISTS accounts_schema;


SET search_path TO accounts_schema;


CREATE TABLE IF NOT EXISTS accounts_schema.accounts (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    login VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    birthdate DATE NOT NULL,
    balance INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_accounts_login ON accounts_schema.accounts(login);
