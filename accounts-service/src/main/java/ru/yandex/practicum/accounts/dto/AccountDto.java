package ru.yandex.practicum.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Brief account summary used in lists")
public record AccountDto(
        @Schema(description = "Account login (username)", example = "ivanov")
        String login,

        @Schema(description = "Full name of the account holder", example = "Ivan Ivanov")
        String name
) {}
