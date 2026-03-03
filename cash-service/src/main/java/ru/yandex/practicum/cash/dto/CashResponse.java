package ru.yandex.practicum.cash.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Response returned after a cash operation is processed")
public record CashResponse(
        @Schema(description = "Updated account balance after the operation, in the smallest currency unit (e.g. kopecks)", example = "150000")
        Long newBalance,

        @Schema(description = "List of error messages if the operation could not be completed fully")
        List<String> errors,

        @Schema(description = "Human-readable informational message about the operation result", example = "Deposit of 50000 completed successfully")
        String info
) {}
