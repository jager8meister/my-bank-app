package ru.yandex.practicum.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.auth.model.User;
import ru.yandex.practicum.auth.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @EventListener(ApplicationReadyEvent.class)
    public Mono<Void> initData() {
        return userRepository.count()
                .filter(count -> count == 0)
                .flatMapMany(count -> Flux.just(
                        new User(null, "ivanov", passwordEncoder.encode("password"), "USER"),
                        new User(null, "petrov", passwordEncoder.encode("password"), "USER"),
                        new User(null, "sidorov", passwordEncoder.encode("password"), "USER")
                ))
                .flatMap(userRepository::save)
                .then();
    }
}
