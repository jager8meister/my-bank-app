
CREATE SCHEMA IF NOT EXISTS accounts_schema;


CREATE TABLE IF NOT EXISTS accounts_schema.accounts (
    id SERIAL PRIMARY KEY,
    login VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    birthdate DATE NOT NULL,
    balance INTEGER NOT NULL DEFAULT 0
);


INSERT INTO accounts_schema.accounts (login, name, birthdate, balance) VALUES
('ivanov', 'Иван Иванов', '1990-01-15', 5000),
('petrov', 'Петр Петров', '1985-05-20', 3000),
('sidorov', 'Сидор Сидоров', '1995-11-30', 1000);
