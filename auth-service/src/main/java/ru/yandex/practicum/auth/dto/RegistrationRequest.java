package ru.yandex.practicum.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Data required to register a new user")
public record RegistrationRequest(
        @Schema(description = "Unique login for the new user; only lowercase letters, digits, underscores or hyphens", example = "ivanov")
        @NotBlank(message = "Login is required")
        @Size(min = 3, max = 20, message = "Login must be 3-20 characters")
        @Pattern(regexp = "[a-z0-9_\\-]+", message = "Login may only contain lowercase letters, digits, _ or -")
        String login,

        @Schema(description = "Password for the new account; must be 6-100 characters", example = "password")
        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 100, message = "Password must be 6-100 characters")
        String password,

        @Schema(description = "Full display name of the user", example = "Ivan Ivanov")
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Schema(description = "Date of birth in ISO-8601 format (YYYY-MM-DD)", example = "1990-05-15")
        @NotNull(message = "Birthdate is required")
        LocalDate birthdate
) {}
