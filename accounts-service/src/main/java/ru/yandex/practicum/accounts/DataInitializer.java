package ru.yandex.practicum.accounts;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.accounts.model.Account;
import ru.yandex.practicum.accounts.repository.AccountRepository;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final int IVANOV_INITIAL_BALANCE = 100;
    private static final int PETROV_INITIAL_BALANCE = 500;
    private static final int SIDOROV_INITIAL_BALANCE = 1000;

    private final AccountRepository accountRepository;

    @Override
    public void run(String... args) {
        Long count = accountRepository.count().block();
        if (count != null && count == 0) {
            accountRepository.save(new Account(
                    null, 0L, "ivanov", "Иванов Иван",
                    LocalDate.of(2001, 1, 1), IVANOV_INITIAL_BALANCE
            )).block();
            accountRepository.save(new Account(
                    null, 0L, "petrov", "Петров Петр",
                    LocalDate.of(1995, 5, 15), PETROV_INITIAL_BALANCE
            )).block();
            accountRepository.save(new Account(
                    null, 0L, "sidorov", "Сидоров Сидор",
                    LocalDate.of(1990, 10, 20), SIDOROV_INITIAL_BALANCE
            )).block();
        }
    }
}
