package ru.yandex.practicum.cash.dto;

import java.util.List;

public record CashResponse(
        Integer newBalance,
        List<String> errors,
        String info
) {
}
