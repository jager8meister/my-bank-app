package ru.yandex.practicum.accounts.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Slf4j
public class AdultValidator implements ConstraintValidator<Adult, LocalDate> {

    @Override
    public boolean isValid(LocalDate birthdate, ConstraintValidatorContext context) {
        if (birthdate == null) {
            return true;
        }
        long age = ChronoUnit.YEARS.between(birthdate, LocalDate.now());
        boolean valid = age >= 18;
        if (!valid) {
            log.warn("Age validation failed: birthdate={} gives age={} (minimum 18 required)", birthdate, age);
        }
        return valid;
    }
}
