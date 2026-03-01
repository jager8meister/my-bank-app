package ru.yandex.practicum.mybankfront.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mybankfront.dto.RegistrationRequest;
import ru.yandex.practicum.mybankfront.service.RegistrationService;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @GetMapping("/register")
    public String showRegistrationForm() {
        return "register";
    }

    @PostMapping("/register")
    public Mono<String> register(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam String name,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthdate,
            Model model
    ) {
        if (login == null || login.length() < 3 || login.length() > 20) {
            model.addAttribute("error", "Логин должен быть от 3 до 20 символов");
            model.addAttribute("login", login);
            model.addAttribute("name", name);
            return Mono.just("register");
        }

        if (!login.matches("[a-z0-9_\\-]+")) {
            model.addAttribute("error", "Логин может содержать только строчные буквы, цифры, _ или -");
            model.addAttribute("login", login);
            model.addAttribute("name", name);
            return Mono.just("register");
        }

        if (name == null || name.isBlank()) {
            model.addAttribute("error", "Имя не должно быть пустым");
            model.addAttribute("login", login);
            return Mono.just("register");
        }

        if (name.length() > 100) {
            model.addAttribute("error", "Имя не должно превышать 100 символов");
            model.addAttribute("login", login);
            model.addAttribute("name", name);
            return Mono.just("register");
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Пароли не совпадают");
            model.addAttribute("login", login);
            model.addAttribute("name", name);
            return Mono.just("register");
        }

        if (password.length() < 6) {
            model.addAttribute("error", "Пароль должен содержать не менее 6 символов");
            model.addAttribute("login", login);
            model.addAttribute("name", name);
            return Mono.just("register");
        }

        if (password.length() > 100) {
            model.addAttribute("error", "Пароль не должен превышать 100 символов");
            model.addAttribute("login", login);
            model.addAttribute("name", name);
            return Mono.just("register");
        }

        if (ChronoUnit.YEARS.between(birthdate, LocalDate.now()) < 18) {
            model.addAttribute("error", "Возраст должен быть не менее 18 лет");
            model.addAttribute("login", login);
            model.addAttribute("name", name);
            return Mono.just("register");
        }

        return registrationService.register(new RegistrationRequest(login, password, name, birthdate))
                .thenReturn("redirect:/?registered=true")
                .onErrorResume(e -> {
                    log.error("Registration failed for {}: {}", login, e.getMessage());
                    model.addAttribute("error", translateError(e.getMessage()));
                    model.addAttribute("login", login);
                    model.addAttribute("name", name);
                    return Mono.just("register");
                });
    }

    private String translateError(String message) {
        if (message == null) return "Ошибка регистрации";
        if (message.contains("Логин уже занят")) return "Логин уже занят";
        if (message.toLowerCase().contains("already exists")) return "Логин уже занят";
        if (message.toLowerCase().contains("conflict")) return "Логин уже занят";
        return "Ошибка регистрации: " + message;
    }
}
