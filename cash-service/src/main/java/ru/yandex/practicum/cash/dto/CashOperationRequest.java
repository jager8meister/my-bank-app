package ru.yandex.practicum.cash.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CashOperationRequest(
        @NotNull(message = "Value is required")
        @Positive(message = "Value must be positive")
        @Max(value = 1_000_000_000L, message = "Amount must not exceed 1000000000")
        Long value,
        @NotNull(message = "Action is required")
        CashAction action
) {
}
