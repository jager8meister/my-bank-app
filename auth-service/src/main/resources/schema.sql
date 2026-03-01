
CREATE SCHEMA IF NOT EXISTS auth_schema;


SET search_path TO auth_schema;


CREATE TABLE IF NOT EXISTS auth_schema.users (
    id BIGSERIAL PRIMARY KEY,
    login VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
);
