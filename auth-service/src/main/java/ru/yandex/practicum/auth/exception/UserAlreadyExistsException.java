package ru.yandex.practicum.auth.exception;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String login) {
        super("Логин уже занят: " + login);
    }
}
