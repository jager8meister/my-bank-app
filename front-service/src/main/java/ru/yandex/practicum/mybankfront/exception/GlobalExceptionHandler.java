package ru.yandex.practicum.mybankfront.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.ui.Model;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import ru.yandex.practicum.mybankfront.service.AccountService;

import java.util.List;
import java.util.Map;

@ControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final AccountService accountService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public String handleInputValidationException(Exception e, Model model, HttpServletRequest request) {
        log.warn("Input validation error: {}", e.getMessage());
        String errorMessage = extractMessage(e);
        return loadAccountPage(model, errorMessage, request);
    }

    private String loadAccountPage(Model model, String errorMessage, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (authentication instanceof OAuth2AuthenticationToken oauth2) {
                String login = oauth2.getName();
                OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                        oauth2.getAuthorizedClientRegistrationId(), login);
                String accessToken = (client != null && client.getAccessToken() != null)
                        ? client.getAccessToken().getTokenValue()
                        : null;
                if (accessToken != null) {
                    Map<String, Object> data = accountService.getAccountInfo(login, accessToken).block();
                    if (data != null) {
                        fillModel(model, data);
                        model.addAttribute("errors", List.of(errorMessage));
                        return "main";
                    }
                }
            }
        } catch (Exception ignored) {
            log.warn("Could not load account data in exception handler", ignored);
        }
        model.addAttribute("name", "");
        model.addAttribute("birthdate", "");
        model.addAttribute("sum", 0);
        model.addAttribute("accounts", List.of());
        model.addAttribute("errors", List.of(errorMessage));
        model.addAttribute("info", null);
        return "main";
    }

    private String extractMessage(Exception e) {
        if (e instanceof MethodArgumentTypeMismatchException) {
            return "Некорректное числовое значение";
        }
        if (e instanceof MissingServletRequestParameterException ex) {
            return "Отсутствует обязательный параметр: " + ex.getParameterName();
        }
        if (e instanceof ConstraintViolationException ex) {
            return ex.getConstraintViolations().stream()
                    .map(ConstraintViolation::getMessage)
                    .findFirst()
                    .orElse("Ошибка валидации");
        }
        return "Ошибка: " + e.getMessage();
    }

    private void fillModel(Model model, Map<String, Object> accountData) {
        model.addAttribute("name", accountData.get("name"));
        model.addAttribute("birthdate", accountData.get("birthdate"));
        model.addAttribute("sum", accountData.get("sum"));
        model.addAttribute("accounts", accountData.get("accounts"));
        model.addAttribute("info", accountData.get("info"));
    }
}
