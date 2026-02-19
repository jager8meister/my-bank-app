

INSERT INTO auth_schema.users (login, password, role) VALUES
    ('ivanov', '$2a$10$EblZqNptyYvcbHZQ/NqMdOO5vWpM3gPKL.lZ3sHZEQd8LKqXbJHnm', 'USER'),
    ('petrov', '$2a$10$EblZqNptyYvcbHZQ/NqMdOO5vWpM3gPKL.lZ3sHZEQd8LKqXbJHnm', 'USER'),
    ('sidorov', '$2a$10$EblZqNptyYvcbHZQ/NqMdOO5vWpM3gPKL.lZ3sHZEQd8LKqXbJHnm', 'USER')
ON CONFLICT (login) DO NOTHING;
