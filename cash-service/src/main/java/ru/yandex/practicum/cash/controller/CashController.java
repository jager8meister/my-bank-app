package ru.yandex.practicum.cash.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.cash.dto.CashOperationRequest;
import ru.yandex.practicum.cash.dto.CashResponse;
import ru.yandex.practicum.cash.service.CashService;
import ru.yandex.practicum.cash.util.AuthorizationUtils;

@RestController
@RequestMapping("/api/cash")
@RequiredArgsConstructor
@Validated
public class CashController {

    private final CashService cashService;

    @PostMapping("/{login}")
    public Mono<CashResponse> processCashOperation(
            @PathVariable @NotBlank(message = "Login is required") String login,
            @RequestBody @Valid CashOperationRequest operation,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkAuthorizationReactive(
                login,
                authentication,
                "You can only perform operations on your own account"
        ).then(cashService.processCashOperation(login, operation));
    }
}
