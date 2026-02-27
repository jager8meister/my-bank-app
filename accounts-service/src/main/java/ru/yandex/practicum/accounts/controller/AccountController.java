package ru.yandex.practicum.accounts.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.dto.AccountResponse;
import ru.yandex.practicum.accounts.dto.CreateAccountRequest;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.service.AccountService;
import ru.yandex.practicum.accounts.util.AuthorizationUtils;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{login}")
    public Mono<AccountResponse> getAccount(
            @PathVariable @NotBlank(message = "Login is required") String login,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkAuthorizationReactive(login, authentication, "You can only access your own account")
                .then(accountService.getAccountInfo(login));
    }

    @PutMapping("/{login}")
    public Mono<AccountResponse> updateAccount(
            @PathVariable @NotBlank(message = "Login is required") String login,
            @RequestBody @Valid UpdateAccountRequest request,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkAuthorizationReactive(login, authentication, "You can only access your own account")
                .then(accountService.updateAccount(login, request));
    }

    @PutMapping("/{login}/balance")
    public Mono<Void> updateBalance(
            @PathVariable @NotBlank(message = "Login is required") String login,
            @RequestParam
            @NotNull(message = "Balance is required")
            @Positive(message = "Balance must be positive")
            Long balance,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkMicroserviceAuthorizationReactive(authentication, "Only microservices can update balance directly")
                .then(accountService.updateBalance(login, balance));
    }

    @GetMapping("/{login}/balance")
    public Mono<Long> getBalance(
            @PathVariable @NotBlank(message = "Login is required") String login,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkAuthorizationReactive(login, authentication, "You can only access your own account")
                .then(accountService.getBalance(login));
    }

    @PostMapping("/{login}/deposit")
    public Mono<Long> depositCash(
            @PathVariable @NotBlank(message = "Login is required") String login,
            @RequestParam
            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            Long amount,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkAuthorizationReactive(login, authentication, "You can only access your own account")
                .then(accountService.depositCash(login, amount));
    }

    @PostMapping("/{login}/withdraw")
    public Mono<Long> withdrawCash(
            @PathVariable @NotBlank(message = "Login is required") String login,
            @RequestParam
            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            Long amount,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkAuthorizationReactive(login, authentication, "You can only access your own account")
                .then(accountService.withdrawCash(login, amount));
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<Void>> createAccount(@RequestBody @Valid CreateAccountRequest request) {
        return accountService.createAccount(request)
                .thenReturn(ResponseEntity.<Void>status(HttpStatus.CREATED).build());
    }

    /**
     * Internal endpoint for transfers - should only be called by transfer-service
     */
    @PostMapping("/internal/transfer")
    public Mono<AccountService.TransferResult> internalTransfer(
            @RequestParam @NotBlank(message = "Sender login is required") String from,
            @RequestParam @NotBlank(message = "Recipient login is required") String to,
            @RequestParam
            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            Long amount,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkMicroserviceAuthorizationReactive(authentication,
                        "Only microservices can call this endpoint")
                .then(accountService.transferMoney(from, to, amount));
    }
}
