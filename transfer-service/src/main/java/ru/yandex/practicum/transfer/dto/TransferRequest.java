package ru.yandex.practicum.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for initiating a money transfer between two accounts")
public record TransferRequest(
        @Schema(description = "Login of the account owner sending the money", example = "ivanov")
        @NotBlank(message = "Sender login is required")
        @Size(max = 20, message = "Sender login must not exceed 20 characters")
        String senderLogin,

        @Schema(description = "Login of the account owner receiving the money", example = "petrov")
        @NotBlank(message = "Recipient login is required")
        @Size(max = 20, message = "Recipient login must not exceed 20 characters")
        String recipientLogin,

        @Schema(description = "Amount to transfer in the smallest currency unit (e.g., kopecks). Must be positive and not exceed 1,000,000,000", example = "5000")
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be positive")
        @Max(value = 1_000_000_000L, message = "Amount must not exceed 1000000000")
        Long amount
) {}
