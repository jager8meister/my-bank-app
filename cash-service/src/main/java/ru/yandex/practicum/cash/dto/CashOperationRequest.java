package ru.yandex.practicum.cash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Request body for a cash deposit or withdrawal operation")
public record CashOperationRequest(
        @Schema(description = "Amount of money in the smallest currency unit (e.g. kopecks)", example = "50000")
        @NotNull(message = "Value is required")
        @Positive(message = "Value must be positive")
        @Max(value = 1_000_000_000L, message = "Amount must not exceed 1000000000")
        Long value,

        @Schema(description = "Type of cash operation: PUT for deposit, GET for withdrawal", example = "PUT")
        @NotNull(message = "Action is required")
        CashAction action
) {}
