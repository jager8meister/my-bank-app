package ru.yandex.practicum.notifications.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload for sending a notification to a bank account holder")
public record NotificationRequestDto(
        @Schema(description = "Username or identifier of the notification recipient", example = "ivanov")
        @NotBlank(message = "Recipient is required")
        String recipient,

        @Schema(description = "Human-readable notification message body", example = "You have sent 500 RUB to petrov")
        @NotBlank(message = "Message is required")
        String message,

        @Schema(description = "Type of the bank event that triggered this notification", example = "TRANSFER_SENT")
        @NotNull(message = "Notification type is required")
        NotificationType type
) {}