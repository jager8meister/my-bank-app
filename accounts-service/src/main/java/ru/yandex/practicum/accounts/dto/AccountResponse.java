package ru.yandex.practicum.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Full account information returned by the accounts service")
public record AccountResponse(
        @Schema(description = "Full name of the account holder", example = "Ivan Ivanov")
        String name,

        @Schema(description = "Date of birth of the account holder", example = "1990-05-20")
        LocalDate birthdate,

        @Schema(description = "Total balance across all accounts in minor currency units (kopecks)", example = "250000")
        Long sum,

        @Schema(description = "List of linked bank accounts belonging to this user")
        List<AccountDto> accounts,

        @Schema(description = "List of validation or processing error messages, if any")
        List<String> errors,

        @Schema(description = "Additional informational message, if any", example = "Account loaded successfully")
        String info
) {}
