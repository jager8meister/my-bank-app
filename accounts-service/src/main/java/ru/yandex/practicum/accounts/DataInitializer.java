package ru.yandex.practicum.accounts;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.model.Account;
import ru.yandex.practicum.accounts.repository.AccountRepository;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private static final long IVANOV_INITIAL_BALANCE = 100L;

    private static final long PETROV_INITIAL_BALANCE = 500L;

    private static final long SIDOROV_INITIAL_BALANCE = 1000L;

    private final AccountRepository accountRepository;

    @EventListener(ApplicationReadyEvent.class)
    public Mono<Void> initData() {
        log.info("Checking whether demo accounts need to be seeded");
        return accountRepository.count()
                .filter(count -> count == 0)
                .doOnNext(count -> log.info("No accounts found — seeding demo accounts"))
                .flatMapMany(count -> Flux.just(
                        new Account(null, 0L, "ivanov", "Иванов Иван",
                                LocalDate.of(2001, 1, 1), IVANOV_INITIAL_BALANCE),
                        new Account(null, 0L, "petrov", "Петров Петр",
                                LocalDate.of(1995, 5, 15), PETROV_INITIAL_BALANCE),
                        new Account(null, 0L, "sidorov", "Сидоров Сидор",
                                LocalDate.of(1990, 10, 20), SIDOROV_INITIAL_BALANCE)
                ))
                .flatMap(accountRepository::save)
                .doOnNext(saved -> log.debug("Demo account seeded: login='{}'", saved.getLogin()))
                .then()
                .doOnSuccess(v -> log.info("Demo account seeding complete"));
    }
}
