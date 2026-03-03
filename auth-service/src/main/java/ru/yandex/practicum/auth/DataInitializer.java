package ru.yandex.practicum.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.yandex.practicum.auth.model.User;
import ru.yandex.practicum.auth.repository.UserRepository;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionalOperator transactionalOperator;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        initData().subscribe(
                null,
                e -> log.error("DataInitializer failed: {}", e.getMessage(), e)
        );
    }

    public Mono<Void> initData() {
        Mono<Void> insert = Flux.just("ivanov", "petrov", "sidorov")
                .flatMap(login -> Mono.fromCallable(
                                () -> new User(null, login, passwordEncoder.encode("password"), "USER"))
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(userRepository::save)
                .then();

        return userRepository.count()
                .filter(count -> count == 0)
                .flatMap(count -> transactionalOperator.transactional(insert));
    }
}
