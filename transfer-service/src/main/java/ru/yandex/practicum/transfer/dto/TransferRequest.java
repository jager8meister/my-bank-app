package ru.yandex.practicum.transfer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransferRequest(
        @NotBlank(message = "Sender login is required")
        String senderLogin,
        @NotBlank(message = "Recipient login is required")
        String recipientLogin,
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        Long amount
) {
}
