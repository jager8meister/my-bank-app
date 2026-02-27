package ru.yandex.practicum.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import ru.yandex.practicum.accounts.validation.Adult;

import java.time.LocalDate;

public record CreateAccountRequest(
        @NotBlank(message = "Login is required")
        String login,

        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Birthdate is required")
        @Adult
        LocalDate birthdate
) {
}
