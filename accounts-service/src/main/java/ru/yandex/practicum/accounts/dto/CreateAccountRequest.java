package ru.yandex.practicum.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.accounts.validation.Adult;

import java.time.LocalDate;

@Schema(description = "Request body for creating a new bank account")
public record CreateAccountRequest(
        @Schema(description = "Unique login for the new account. Must be 3–20 characters, lowercase letters, digits, underscore or hyphen only.", example = "ivanov")
        @NotBlank(message = "Login is required")
        @Size(min = 3, max = 20, message = "Login must be between 3 and 20 characters")
        @Pattern(regexp = "[a-z0-9_\\-]+", message = "Login may only contain lowercase letters, digits, _ or -")
        String login,

        @Schema(description = "Full name of the account holder, max 100 characters", example = "Ivan Ivanov")
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Schema(description = "Date of birth of the account holder. Must be at least 18 years ago.", example = "1990-05-20")
        @NotNull(message = "Birthdate is required")
        @Adult
        LocalDate birthdate
) {}
