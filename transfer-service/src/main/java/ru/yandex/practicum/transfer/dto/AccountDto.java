package ru.yandex.practicum.transfer.dto;

import java.time.LocalDate;

public record AccountDto(
        Long id,
        String login,
        String name,
        LocalDate birthdate,
        Long balance
) {
}
