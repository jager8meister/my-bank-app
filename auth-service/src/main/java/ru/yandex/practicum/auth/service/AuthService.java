package ru.yandex.practicum.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import ru.yandex.practicum.auth.dto.AuthRequest;
import ru.yandex.practicum.auth.dto.AuthResponse;
import ru.yandex.practicum.auth.model.User;
import ru.yandex.practicum.auth.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Mono<AuthResponse> authenticate(AuthRequest request) {
        return userRepository.findByLogin(request.login())
                .flatMap(user -> Mono.fromCallable(
                                () -> passwordEncoder.matches(request.password(), user.getPassword()))
                        .subscribeOn(Schedulers.boundedElastic())
                        .filter(Boolean::booleanValue)
                        .map(ok -> new AuthResponse(user.getLogin(), user.getRole(), true)))
                .defaultIfEmpty(new AuthResponse(null, null, false));
    }

    @Transactional(readOnly = true)
    public Mono<User> validateUser(String login) {
        return userRepository.findByLogin(login);
    }
}
