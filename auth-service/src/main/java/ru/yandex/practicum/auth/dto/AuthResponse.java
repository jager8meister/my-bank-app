package ru.yandex.practicum.auth.dto;

public record AuthResponse(
        String login,
        String role,
        boolean authenticated
) {}
