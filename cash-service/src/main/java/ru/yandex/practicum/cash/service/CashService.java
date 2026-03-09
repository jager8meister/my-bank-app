package ru.yandex.practicum.cash.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.cash.dto.CashAction;
import ru.yandex.practicum.cash.dto.CashOperationRequest;
import ru.yandex.practicum.cash.dto.CashResponse;
import ru.yandex.practicum.cash.dto.NotificationEvent;
import ru.yandex.practicum.cash.exception.CashOperationException;
import ru.yandex.practicum.cash.exception.InsufficientFundsException;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashService {

    private final WebClient webClient;

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    private final MeterRegistry meterRegistry;

    @Value("${services.accounts.host:accounts-service}")
    private String accountsServiceHost;

    @Value("${services.accounts.port:8081}")
    private int accountsServicePort;

    @CircuitBreaker(name = "accounts-service", fallbackMethod = "fallbackCashOperation")
    @Retry(name = "accounts-service")
    public Mono<CashResponse> processCashOperation(String login, CashOperationRequest operation) {
        log.info("Processing cash operation: login={}, action={}, amount={}", login, operation.action(), operation.value());
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
                        if (operation.action() == CashAction.GET) {
                            meterRegistry.counter("cash.withdrawal.failures", "login", login, "reason", "insufficient_funds").increment();
                        }
                        return Mono.error(new InsufficientFundsException("Недостаточно средств на счету"));
                    }
                    meterRegistry.counter("cash.operation.failures", "login", login, "reason", "client_error", "action", operation.action().name()).increment();
                    return Mono.error(new CashOperationException("Ошибка операции: " + response.statusCode()));
                })
                .onStatus(HttpStatusCode::is5xxServerError, response -> {
                    log.error("5xx error from accounts-service: status={}", response.statusCode());
                    return Mono.error(new CashOperationException("Сервис счетов недоступен: " + response.statusCode()));
                })
                .bodyToMono(Long.class)
                .map(newBalance -> {
                    log.info("Cash operation completed: login={}, action={}, amount={}, newBalance={}", login, operation.action(), operation.value(), newBalance);
                    return new CashResponse(
                            newBalance,
                            null,
                            operation.action() == CashAction.GET
                                    ? "Снято " + operation.value() + " руб"
                                    : "Положено " + operation.value() + " руб"
                    );
                })
                .doOnSuccess(response -> {
                    String type = operation.action() == CashAction.GET ? "CASH_WITHDRAWAL" : "CASH_DEPOSIT";
                    log.info("Sending Kafka notification: login={}, type={}", login, type);
                    kafkaTemplate.send("notifications", login,
                            new NotificationEvent(login, type, response.info(), LocalDateTime.now().toString()))
                            .whenComplete((result, ex) -> {
                                if (ex != null) {
                                    log.warn("Failed to send Kafka notification: login={}, type={}, error={}", login, type, ex.getMessage());
                                } else {
                                    log.debug("Kafka notification sent successfully: login={}, type={}, offset={}", login, type, result.getRecordMetadata().offset());
                                }
                            });
                })
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
        meterRegistry.counter("cash.operation.failures", "login", login, "reason", "circuit_breaker", "action", operation.action().name()).increment();
        return Mono.error(new CashOperationException("Сервис временно недоступен. Попробуйте позже."));
    }
}
