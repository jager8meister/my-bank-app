package ru.yandex.practicum.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.yandex.practicum.auth.dto.AuthRequest;
import ru.yandex.practicum.auth.dto.AuthResponse;
import ru.yandex.practicum.auth.model.User;
import ru.yandex.practicum.auth.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Mono<AuthResponse> authenticate(AuthRequest request) {
        log.info("Authenticating user: {}", request.login());
        return userRepository.findByLogin(request.login())
                .flatMap(user -> Mono.fromCallable(
                                () -> passwordEncoder.matches(request.password(), user.getPassword()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .filter(Boolean::booleanValue)
                        .map(ok -> {
                            log.info("Authentication successful for user: {}", request.login());
                            return new AuthResponse(user.getLogin(), user.getRole(), true);
                        }))
                .defaultIfEmpty(new AuthResponse(null, null, false))
                .doOnNext(response -> {
                    if (!response.authenticated()) {
                        log.warn("Authentication failed for user: {} - invalid credentials or user not found", request.login());
                    }
                });
    }

    @Transactional(readOnly = true)
    public Mono<User> validateUser(String login) {
        log.info("Validating user existence for login: {}", login);
        return userRepository.findByLogin(login)
                .doOnNext(user -> log.debug("User found: {}", login))
                .doOnSuccess(user -> {
                    if (user == null) {
                        log.warn("User not found for login: {}", login);
                    }
                });
    }
}
