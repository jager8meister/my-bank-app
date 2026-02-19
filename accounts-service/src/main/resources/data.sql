
INSERT INTO accounts_schema.accounts (login, name, birthdate, balance) VALUES
    ('ivanov', 'Иванов Иван', '2001-01-01', 100),
    ('petrov', 'Петров Петр', '1995-05-15', 500),
    ('sidorov', 'Сидоров Сидор', '1990-10-20', 1000)
ON CONFLICT (login) DO NOTHING;
