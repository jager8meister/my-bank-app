package ru.yandex.practicum.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.accounts.validation.Adult;

import java.time.LocalDate;

public record CreateAccountRequest(
        @NotBlank(message = "Login is required")
        @Size(min = 3, max = 20, message = "Login must be between 3 and 20 characters")
        @Pattern(regexp = "[a-z0-9_\\-]+", message = "Login may only contain lowercase letters, digits, _ or -")
        String login,

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @NotNull(message = "Birthdate is required")
        @Adult
        LocalDate birthdate
) {
}
