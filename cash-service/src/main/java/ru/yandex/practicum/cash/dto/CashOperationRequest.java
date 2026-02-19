package ru.yandex.practicum.cash.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CashOperationRequest(
        @NotNull(message = "Value is required")
        @Positive(message = "Value must be positive")
        Integer value,
        @NotNull(message = "Action is required")
        CashAction action
) {
}
