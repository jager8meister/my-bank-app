package ru.yandex.practicum.notifications.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Type of bank event that triggered the notification: " +
        "TRANSFER_SENT — outgoing transfer, " +
        "TRANSFER_RECEIVED — incoming transfer, " +
        "BALANCE_LOW — account balance fell below threshold, " +
        "ACCOUNT_UPDATED — account details were changed")
public enum NotificationType {
    TRANSFER_SENT,
    TRANSFER_RECEIVED,
    BALANCE_LOW,
    ACCOUNT_UPDATED
}
