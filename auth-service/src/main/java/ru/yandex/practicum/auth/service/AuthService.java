package ru.yandex.practicum.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.auth.dto.AuthRequest;
import ru.yandex.practicum.auth.dto.AuthResponse;
import ru.yandex.practicum.auth.model.User;
import ru.yandex.practicum.auth.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Mono<AuthResponse> authenticate(AuthRequest request) {
        return userRepository.findByLogin(request.login())
                .filter(user -> passwordEncoder.matches(request.password(), user.getPassword()))
                .map(user -> new AuthResponse(user.getLogin(), user.getRole(), true))
                .defaultIfEmpty(new AuthResponse(null, null, false));
    }

    public Mono<User> validateUser(String login) {
        return userRepository.findByLogin(login);
    }
}
