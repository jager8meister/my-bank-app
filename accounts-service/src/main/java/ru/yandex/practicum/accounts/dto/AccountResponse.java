package ru.yandex.practicum.accounts.dto;

import java.time.LocalDate;
import java.util.List;

public record AccountResponse(
        String name,
        LocalDate birthdate,
        Integer sum,
        List<AccountDto> accounts,
        List<String> errors,
        String info
) {
}
