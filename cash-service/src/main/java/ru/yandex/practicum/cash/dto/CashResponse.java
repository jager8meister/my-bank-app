package ru.yandex.practicum.cash.dto;

import java.util.List;

public record CashResponse(
        Long newBalance,
        List<String> errors,
        String info
) {
}
