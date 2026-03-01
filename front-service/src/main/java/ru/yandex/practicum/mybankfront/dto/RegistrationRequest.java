package ru.yandex.practicum.mybankfront.dto;

import java.time.LocalDate;

public record RegistrationRequest(
        String login,
        String password,
        String name,
        LocalDate birthdate
) {}
