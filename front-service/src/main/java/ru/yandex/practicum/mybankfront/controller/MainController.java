package ru.yandex.practicum.mybankfront.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mybankfront.dto.CashAction;
import ru.yandex.practicum.mybankfront.service.AccountService;
import ru.yandex.practicum.mybankfront.store.NotificationStore;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@Validated
public class MainController {

    private final AccountService accountService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final NotificationStore notificationStore;

    @GetMapping
    public String index() {
        return "redirect:/account";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/account")
    public Mono<String> getAccount(
            Model model,
            Authentication authentication
    ) {
        String login = authentication.getName();
        log.info("GET /account requested by user: {}", login);
        String accessToken = getAccessToken(authentication);
        return accountService.getAccountInfo(login, accessToken)
                .map(accountData -> {
                    fillModel(model, accountData);
                    String pending = notificationStore.pop(login);
                    if (pending != null) model.addAttribute("info", pending);
                    return "main";
                });
    }

    @PostMapping("/account")
    public Mono<String> editAccount(
            Model model,
            Authentication authentication,
            @RequestParam("name") @NotBlank(message = "Имя не должно быть пустым") @Size(max = 100, message = "Имя не должно превышать 100 символов") String name,
            @RequestParam("birthdate") @NotNull(message = "Birthdate is required") @Past(message = "Birthdate must be in the past") LocalDate birthdate
    ) {
        String login = authentication.getName();
        log.info("POST /account (edit profile) requested by user: {}", login);
        String accessToken = getAccessToken(authentication);
        return accountService.updateAccount(login, name, birthdate, accessToken)
                .map(accountData -> {
                    fillModel(model, accountData);
                    if (accountData.get("errors") == null) {
                        model.addAttribute("info", "Данные профиля успешно сохранены");
                    }
                    return "main";
                });
    }

    @PostMapping("/cash")
    public Mono<String> editCash(
            Model model,
            Authentication authentication,
            @RequestParam("value") @Positive(message = "Сумма должна быть положительной") @Max(value = 1_000_000_000L, message = "Сумма не может превышать 1 000 000 000 руб") long value,
            @RequestParam("action") @NotNull(message = "Action is required") CashAction action
    ) {
        String login = authentication.getName();
        log.info("POST /cash action={} requested by user: {}", action, login);
        String accessToken = getAccessToken(authentication);
        return accountService.processCash(login, value, action, accessToken)
                .doOnNext(accountData -> fillModel(model, accountData))
                .thenReturn("main");
    }

    @PostMapping("/transfer")
    public Mono<String> transfer(
            Model model,
            Authentication authentication,
            @RequestParam("value") @Positive(message = "Сумма должна быть положительной") @Max(value = 1_000_000_000L, message = "Сумма не может превышать 1 000 000 000 руб") long value,
            @RequestParam("login") @NotBlank(message = "Получатель не указан") @Size(max = 20, message = "Логин получателя не должен превышать 20 символов") String toLogin
    ) {
        String fromLogin = authentication.getName();
        log.info("POST /transfer from user: {} to recipient: {}", fromLogin, toLogin);
        String accessToken = getAccessToken(authentication);
        return accountService.transfer(fromLogin, value, toLogin, accessToken)
                .doOnNext(accountData -> fillModel(model, accountData))
                .thenReturn("main");
    }

    private String getAccessToken(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauth2) {
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                    oauth2.getAuthorizedClientRegistrationId(),
                    oauth2.getName()
            );
            if (client != null && client.getAccessToken() != null) {
                return client.getAccessToken().getTokenValue();
            }
        }
        return "";
    }

    private void fillModel(Model model, Map<String, Object> accountData) {
        model.addAttribute("name", accountData.get("name"));
        model.addAttribute("birthdate", accountData.get("birthdate"));
        model.addAttribute("sum", accountData.get("sum"));
        model.addAttribute("accounts", accountData.get("accounts"));
        model.addAttribute("errors", accountData.get("errors"));
        model.addAttribute("info", accountData.get("info"));
    }
}
