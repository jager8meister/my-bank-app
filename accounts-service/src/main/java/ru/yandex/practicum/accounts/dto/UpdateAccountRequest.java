package ru.yandex.practicum.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.yandex.practicum.accounts.validation.Adult;

import java.time.LocalDate;

public record UpdateAccountRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must not exceed 100 characters")
        String name,

        @NotNull(message = "Birthdate is required")
        @Adult
        LocalDate birthdate
) {
}
