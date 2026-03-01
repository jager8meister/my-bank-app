package ru.yandex.practicum.transfer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TransferRequest(
        @NotBlank(message = "Sender login is required")
        @Size(max = 20, message = "Sender login must not exceed 20 characters")
        String senderLogin,
        @NotBlank(message = "Recipient login is required")
        @Size(max = 20, message = "Recipient login must not exceed 20 characters")
        String recipientLogin,
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @Max(value = 1_000_000_000L, message = "Amount must not exceed 1000000000")
        Long amount
) {
}
