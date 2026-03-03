package ru.yandex.practicum.notifications.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a notification delivery attempt")
public record NotificationResponseDto(
        @Schema(description = "Indicates whether the notification was processed successfully", example = "true")
        Boolean success,

        @Schema(description = "Human-readable status message describing the outcome", example = "Notification sent successfully")
        String message
) {}