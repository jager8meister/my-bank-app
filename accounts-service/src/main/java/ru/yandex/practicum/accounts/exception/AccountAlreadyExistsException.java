package ru.yandex.practicum.accounts.exception;

public class AccountAlreadyExistsException extends RuntimeException {
    public AccountAlreadyExistsException(String login) {
        super("Account already exists: " + login);
    }
}
