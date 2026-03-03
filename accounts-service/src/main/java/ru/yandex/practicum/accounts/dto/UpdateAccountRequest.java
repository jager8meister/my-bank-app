package ru.yandex.practicum.accounts.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.accounts.validation.Adult;

import java.time.LocalDate;

@Schema(description = "Request body for updating account details")
public record UpdateAccountRequest(
        @Schema(description = "Updated full name of the account holder, max 100 characters", example = "Ivan A. Ivanov")
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @Schema(description = "Updated date of birth. Must be at least 18 years ago.", example = "1990-05-20")
        @NotNull(message = "Birthdate is required")
        @Adult
        LocalDate birthdate
) {}
