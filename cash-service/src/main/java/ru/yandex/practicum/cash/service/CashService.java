package ru.yandex.practicum.cash.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.cash.client.NotificationClient;
import ru.yandex.practicum.cash.dto.CashAction;
import ru.yandex.practicum.cash.dto.CashOperationRequest;
import ru.yandex.practicum.cash.dto.CashResponse;
import ru.yandex.practicum.cash.exception.CashOperationException;
import ru.yandex.practicum.cash.exception.InsufficientFundsException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashService {

    private final WebClient webClient;
    private final NotificationClient notificationClient;

    @Value("${services.accounts.host:accounts-service}")
    private String accountsServiceHost;

    @CircuitBreaker(name = "accounts-service", fallbackMethod = "fallbackCashOperation")
    @Retry(name = "accounts-service")
    @TimeLimiter(name = "accounts-service")
    public Mono<CashResponse> processCashOperation(String login, CashOperationRequest operation) {
        String endpoint = operation.action() == CashAction.GET
                ? "/api/accounts/{login}/withdraw"
                : "/api/accounts/{login}/deposit";
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("lb")
                        .host(accountsServiceHost)
                        .path(endpoint)
                        .queryParam("amount", operation.value())
                        .build(login))
                .retrieve()
                .bodyToMono(Long.class)
                .flatMap(newBalance -> {
                    String info = operation.action() == CashAction.GET
                            ? "Снято " + operation.value() + " руб"
                            : "Положено " + operation.value() + " руб";
                    String notificationType = operation.action() == CashAction.GET
                            ? "BALANCE_LOW"
                            : "ACCOUNT_UPDATED";
                    return notificationClient.sendCashOperationNotification(
                            login,
                            info + ". Новый баланс: " + newBalance + " руб",
                            notificationType
                    )
                    .onErrorResume(notificationError -> {
                        log.warn("Failed to send notification, but continuing operation", notificationError);
                        return Mono.empty();
                    })
                    .then(Mono.just(new CashResponse(newBalance, null, info)));
                })
                .onErrorResume(WebClientResponseException.class, error -> {
                    log.error("HTTP error from accounts-service: status={}, body={}", error.getStatusCode(), error.getResponseBodyAsString());
                    if (error.getStatusCode() == HttpStatus.BAD_REQUEST &&
                        error.getResponseBodyAsString().contains("Insufficient funds")) {
                        return Mono.error(new InsufficientFundsException("Недостаточно средств на счету"));
                    }
                    return Mono.error(new CashOperationException("Ошибка операции: " + error.getMessage()));
                })
                .onErrorResume(error -> {
                    if (error instanceof InsufficientFundsException) {
                        return Mono.error(error);
                    }
                    String errorMessage = error.getMessage();
                    if (errorMessage != null && errorMessage.contains("Insufficient funds")) {
                        return Mono.error(new InsufficientFundsException("Недостаточно средств на счету"));
                    }
                    log.error("Unexpected error during cash operation", error);
                    return Mono.error(new CashOperationException("Ошибка операции: " + error.getMessage()));
                });
    }

    private Mono<CashResponse> fallbackCashOperation(String login, CashOperationRequest operation, Exception ex) {
        log.error("Circuit breaker fallback triggered for cash operation. Service unavailable.", ex);
        return Mono.error(new CashOperationException("Сервис временно недоступен. Попробуйте позже."));
    }
}
