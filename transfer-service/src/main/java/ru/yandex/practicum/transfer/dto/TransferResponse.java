package ru.yandex.practicum.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Result of a money transfer operation")
public record TransferResponse(
        @Schema(description = "Indicates whether the transfer was completed successfully", example = "true")
        boolean success,

        @Schema(description = "Human-readable message describing the outcome of the transfer", example = "Transfer completed successfully")
        String message,

        @Schema(description = "Remaining balance of the sender's account after the transfer, in the smallest currency unit", example = "45000")
        Long senderBalance,

        @Schema(description = "Remaining balance of the recipient's account after the transfer, in the smallest currency unit", example = "55000")
        Long recipientBalance
) {}
