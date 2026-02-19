package ru.yandex.practicum.notifications.dto;

public record NotificationResponseDto(
        Boolean success,

        String message
) {}