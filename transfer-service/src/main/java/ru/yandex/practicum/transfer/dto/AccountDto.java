package ru.yandex.practicum.transfer.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Internal DTO representing account data retrieved from the accounts-service")
public record AccountDto(
        @Schema(description = "Unique identifier of the account", example = "1")
        Long id,

        @Schema(description = "Login name of the account owner", example = "ivanov")
        String login,

        @Schema(description = "Full name of the account owner", example = "Ivan Ivanov")
        String name,

        @Schema(description = "Date of birth of the account owner", example = "1990-05-15")
        LocalDate birthdate,

        @Schema(description = "Current account balance in the smallest currency unit (e.g., kopecks)", example = "50000")
        Long balance
) {}
