package ru.yandex.practicum.accounts.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record UpdateAccountRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotNull(message = "Birthdate is required")
        @Past(message = "Birthdate must be in the past")
        LocalDate birthdate
) {
    @AssertTrue(message = "Age must be at least 18 years")
    public boolean isBirthdateValid() {
        return birthdate == null || ChronoUnit.YEARS.between(birthdate, LocalDate.now()) >= 18;
    }
}
