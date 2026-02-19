package ru.yandex.practicum.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.auth.model.User;
import ru.yandex.practicum.auth.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        Long count = userRepository.count().block();
        if (count != null && count == 0) {
            userRepository.save(new User(null, "ivanov", passwordEncoder.encode("password"), "USER")).block();
            userRepository.save(new User(null, "petrov", passwordEncoder.encode("password"), "USER")).block();
            userRepository.save(new User(null, "sidorov", passwordEncoder.encode("password"), "USER")).block();
        }
    }
}
