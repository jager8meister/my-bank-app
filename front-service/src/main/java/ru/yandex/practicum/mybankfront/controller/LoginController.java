package ru.yandex.practicum.mybankfront.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.yandex.practicum.mybankfront.dto.TokenResponse;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LoginController {

    private final WebClient webClient;

    @Value("${gateway.url:http://gateway-service:8080}")
    private String gatewayUrl;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @PostMapping("/login")
    public String loginSubmit(
            @RequestParam String login,
            @RequestParam String password,
            HttpSession session,
            Model model) {
        try {
            TokenResponse tokenResponse = webClient.post()
                    .uri(gatewayUrl + "/api/auth/token")
                    .bodyValue(new LoginRequest(login, password))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .block();

            if (tokenResponse == null || tokenResponse.accessToken() == null) {
                model.addAttribute("error", "Ошибка авторизации");
                return "login";
            }

            session.setAttribute("ACCESS_TOKEN", tokenResponse.accessToken());
            session.setAttribute("REFRESH_TOKEN", tokenResponse.refreshToken());
            session.setAttribute("TOKEN_EXPIRES_AT",
                    System.currentTimeMillis() / 1000 + tokenResponse.expiresIn() - 30);

            log.info("User {} logged in successfully", login);
            return "redirect:/account";

        } catch (WebClientResponseException e) {
            log.warn("Login failed for {}: HTTP {}", login, e.getStatusCode().value());
            model.addAttribute("error", "Неверный логин или пароль");
            return "login";
        } catch (Exception e) {
            log.error("Login error for {}: {}", login, e.getMessage());
            model.addAttribute("error", "Ошибка сервера, попробуйте позже");
            return "login";
        }
    }

    private record LoginRequest(String login, String password) {}
}
