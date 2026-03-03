package ru.yandex.practicum.cash.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.cash.dto.CashAction;
import ru.yandex.practicum.cash.dto.CashOperationRequest;
import ru.yandex.practicum.cash.dto.CashResponse;
import ru.yandex.practicum.cash.exception.CashOperationException;
import ru.yandex.practicum.cash.exception.InsufficientFundsException;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashService {

    private final WebClient webClient;

    @Value("${services.accounts.host:accounts-service}")
    private String accountsServiceHost;

    @Value("${services.accounts.port:8081}")
    private int accountsServicePort;

    @CircuitBreaker(name = "accounts-service", fallbackMethod = "fallbackCashOperation")
    @Retry(name = "accounts-service")
    public Mono<CashResponse> processCashOperation(String login, CashOperationRequest operation) {
        String endpoint = operation.action() == CashAction.GET
                ? "/api/accounts/{login}/withdraw"
                : "/api/accounts/{login}/deposit";
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("http")
                        .host(accountsServiceHost)
                        .port(accountsServicePort)
                        .path(endpoint)
                        .queryParam("amount", operation.value())
                        .build(login))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    log.error("4xx error from accounts-service: status={}", response.statusCode());
                    if (response.statusCode() == HttpStatus.BAD_REQUEST) {
                        return Mono.error(new InsufficientFundsException("Недостаточно средств на счету"));
                    }
                    return Mono.error(new CashOperationException("Ошибка операции: " + response.statusCode()));
                })
                .onStatus(HttpStatusCode::is5xxServerError, response -> {
                    log.error("5xx error from accounts-service: status={}", response.statusCode());
                    return Mono.error(new CashOperationException("Сервис счетов недоступен: " + response.statusCode()));
                })
                .bodyToMono(Long.class)
                .map(newBalance -> new CashResponse(
                        newBalance,
                        null,
                        operation.action() == CashAction.GET
                                ? "Снято " + operation.value() + " руб"
                                : "Положено " + operation.value() + " руб"
                ))
                .timeout(Duration.ofSeconds(3))
                .onErrorResume(error -> {
                    if (error instanceof InsufficientFundsException || error instanceof CashOperationException) {
                        return Mono.error(error);
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
