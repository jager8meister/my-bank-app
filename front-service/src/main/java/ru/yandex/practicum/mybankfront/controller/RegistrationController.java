package ru.yandex.practicum.mybankfront.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mybankfront.dto.NotificationEvent;
import ru.yandex.practicum.mybankfront.dto.RegistrationRequest;
import ru.yandex.practicum.mybankfront.service.RegistrationService;
import ru.yandex.practicum.mybankfront.store.NotificationStore;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final NotificationStore notificationStore;

    @GetMapping("/register")
    public String showRegistrationForm(HttpSession session, Model model) {
        String error = (String) session.getAttribute("flash_error");
        if (error != null) {
            session.removeAttribute("flash_error");
            model.addAttribute("error", error);
        }
        String savedLogin = (String) session.getAttribute("flash_login");
        if (savedLogin != null) {
            session.removeAttribute("flash_login");
            model.addAttribute("login", savedLogin);
        }
        String savedName = (String) session.getAttribute("flash_name");
        if (savedName != null) {
            session.removeAttribute("flash_name");
            model.addAttribute("name", savedName);
        }
        return "register";
    }

    @PostMapping("/register")
    public Mono<String> register(
            @RequestParam String login,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam String name,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate birthdate,
            Model model,
            HttpSession session
    ) {
        if (login == null || login.length() < 3 || login.length() > 20) {
            return errorRedirect(login, name, "Логин должен быть от 3 до 20 символов", session);
        }

        if (!login.matches("[a-z0-9_\\-]+")) {
            return errorRedirect(login, name, "Логин может содержать только строчные буквы, цифры, _ или -", session);
        }

        if (name == null || name.isBlank()) {
            return errorRedirect(login, null, "Имя не должно быть пустым", session);
        }

        if (name.length() > 100) {
            return errorRedirect(login, name, "Имя не должно превышать 100 символов", session);
        }

        if (!password.equals(confirmPassword)) {
            return errorRedirect(login, name, "Пароли не совпадают", session);
        }

        if (password.length() < 6) {
            return errorRedirect(login, name, "Пароль должен содержать не менее 6 символов", session);
        }

        if (password.length() > 100) {
            return errorRedirect(login, name, "Пароль не должен превышать 100 символов", session);
        }

        if (ChronoUnit.YEARS.between(birthdate, LocalDate.now()) < 18) {
            return errorRedirect(login, name, "Возраст должен быть не менее 18 лет", session);
        }

        log.info("Registration attempt for login: {}", login);
        return registrationService.register(new RegistrationRequest(login, password, name, birthdate))
                .then(Mono.defer(() -> {
                    NotificationEvent event = new NotificationEvent(
                            login, "REGISTRATION",
                            "Регистрация прошла успешно! Войдите в аккаунт.",
                            LocalDateTime.now().toString());
                    kafkaTemplate.send("notifications", login, event);
                    return Mono.defer(() -> {
                                String msg = notificationStore.pop(login);
                                return msg != null ? Mono.just(msg) : Mono.empty();
                            })
                            .repeatWhenEmpty(flux -> flux.take(19).delayElements(Duration.ofMillis(100)))
                            .timeout(Duration.ofSeconds(2))
                            .onErrorResume(e -> Mono.empty())
                            .doOnNext(notification -> session.setAttribute("flash_notification", notification))
                            .thenReturn("redirect:/login");
                }))
                .onErrorResume(e -> {
                    log.error("Registration failed for {}: {}", login, e.getMessage());
                    String errorMessage;
                    if (e instanceof WebClientResponseException webEx) {
                        String body = webEx.getResponseBodyAsString();
                        if (body.contains("\"error\"")) {
                            try {
                                Map<?, ?> json = objectMapper.readValue(body, Map.class);
                                Object err = json.get("error");
                                errorMessage = err != null ? err.toString() : translateError(body);
                            } catch (Exception jsonEx) {
                                log.warn("Failed to parse error response as JSON: {}", body);
                                errorMessage = translateError(body);
                            }
                        } else {
                            errorMessage = translateError(webEx.getMessage());
                        }
                    } else {
                        errorMessage = translateError(e.getMessage());
                    }
                    return errorRedirect(login, name, errorMessage, session);
                });
    }

    private Mono<String> errorRedirect(String login, String name, String errorMessage, HttpSession session) {
        String key = (login != null && !login.isBlank()) ? login : "error_" + session.getId().substring(0, 8);
        NotificationEvent event = new NotificationEvent(key, "ERROR", errorMessage, LocalDateTime.now().toString());
        kafkaTemplate.send("notifications", key, event);
        return Mono.defer(() -> {
                    String msg = notificationStore.pop(key);
                    return msg != null ? Mono.just(msg) : Mono.empty();
                })
                .repeatWhenEmpty(flux -> flux.take(19).delayElements(Duration.ofMillis(100)))
                .timeout(Duration.ofSeconds(2))
                .onErrorResume(e -> Mono.empty())
                .doOnNext(notification -> {
                    session.setAttribute("flash_error", notification);
                    if (login != null && !login.isBlank()) session.setAttribute("flash_login", login);
                    if (name != null && !name.isBlank()) session.setAttribute("flash_name", name);
                })
                .thenReturn("redirect:/register");
    }

    private String translateError(String message) {
        if (message == null) return "Ошибка регистрации";
        if (message.contains("Логин уже занят")) return "Логин уже занят";
        if (message.toLowerCase().contains("already exists")) return "Логин уже занят";
        if (message.toLowerCase().contains("conflict")) return "Логин уже занят";
        return "Ошибка регистрации: " + message;
    }
}
