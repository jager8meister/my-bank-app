package ru.yandex.practicum.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationRequestDto(
        @NotBlank(message = "Recipient is required")
        String recipient,

        @NotBlank(message = "Message is required")
        String message,

        @NotNull(message = "Notification type is required")
        NotificationType type
) {}