package ru.yandex.practicum.mybankfront.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mybankfront.dto.CashAction;
import ru.yandex.practicum.mybankfront.service.AccountService;

import java.time.LocalDate;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Validated
public class MainController {

    private final AccountService accountService;

    @GetMapping
    public String index() {
        return "redirect:/account";
    }

    @GetMapping("/account")
    public Mono<String> getAccount(
            Model model,
            Authentication authentication,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient
    ) {
        String login = authentication.getName();
        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        return accountService.getAccountInfo(login, accessToken)
                .doOnNext(accountData -> fillModel(model, accountData))
                .thenReturn("main");
    }

    @PostMapping("/account")
    public Mono<String> editAccount(
            Model model,
            Authentication authentication,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient,
            @RequestParam("name") @NotBlank(message = "Name is required") String name,
            @RequestParam("birthdate") @NotNull(message = "Birthdate is required") @Past(message = "Birthdate must be in the past") LocalDate birthdate
    ) {
        String login = authentication.getName();
        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        return accountService.updateAccount(login, name, birthdate, accessToken)
                .doOnNext(accountData -> fillModel(model, accountData))
                .thenReturn("main");
    }

    @PostMapping("/cash")
    public Mono<String> editCash(
            Model model,
            Authentication authentication,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient,
            @RequestParam("value") @Positive(message = "Value must be positive") long value,
            @RequestParam("action") @NotNull(message = "Action is required") CashAction action
    ) {
        String login = authentication.getName();
        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        return accountService.processCash(login, value, action, accessToken)
                .doOnNext(accountData -> fillModel(model, accountData))
                .thenReturn("main");
    }

    @PostMapping("/transfer")
    public Mono<String> transfer(
            Model model,
            Authentication authentication,
            @RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient,
            @RequestParam("value") @Positive(message = "Value must be positive") long value,
            @RequestParam("login") @NotBlank(message = "Recipient login is required") String toLogin
    ) {
        String fromLogin = authentication.getName();
        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        return accountService.transfer(fromLogin, value, toLogin, accessToken)
                .doOnNext(accountData -> fillModel(model, accountData))
                .thenReturn("main");
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
