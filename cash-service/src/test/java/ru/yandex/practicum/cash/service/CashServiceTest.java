package ru.yandex.practicum.cash.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.cash.dto.CashAction;
import ru.yandex.practicum.cash.dto.CashOperationRequest;
import ru.yandex.practicum.cash.dto.CashResponse;
import ru.yandex.practicum.cash.dto.NotificationEvent;
import ru.yandex.practicum.cash.exception.CashOperationException;
import ru.yandex.practicum.cash.exception.InsufficientFundsException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CashService Unit Tests")
class CashServiceTest {

    private WireMockServer wireMockServer;

    private CashService cashService;

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate = Mockito.mock(KafkaTemplate.class);

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor("localhost", wireMockServer.port());

        WebClient webClient = WebClient.builder().build();
        cashService = new CashService(webClient, kafkaTemplate, new SimpleMeterRegistry());

        ReflectionTestUtils.setField(cashService, "accountsServiceHost", "localhost");
        ReflectionTestUtils.setField(cashService, "accountsServicePort", wireMockServer.port());
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    @DisplayName("Successful deposit (PUT) — accounts-service returns 200 with new balance")
    void shouldProcessDepositSuccessfully() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/accounts/ivanov/deposit"))
                .withQueryParam("amount", equalTo("500"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("5500")));

        CashOperationRequest request = new CashOperationRequest(500L, CashAction.PUT);

        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.newBalance()).isEqualTo(5500L);
                    assertThat(response.info()).contains("Положено 500 руб");
                    assertThat(response.errors()).isNull();
                })
                .verifyComplete();

        verify(kafkaTemplate).send(eq("notifications"), eq("ivanov"), any(NotificationEvent.class));
    }

    @Test
    @DisplayName("Successful withdrawal (GET) — accounts-service returns 200 with new balance")
    void shouldProcessWithdrawalSuccessfully() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/accounts/ivanov/withdraw"))
                .withQueryParam("amount", equalTo("500"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("4500")));

        CashOperationRequest request = new CashOperationRequest(500L, CashAction.GET);

        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.newBalance()).isEqualTo(4500L);
                    assertThat(response.info()).contains("Снято 500 руб");
                    assertThat(response.errors()).isNull();
                })
                .verifyComplete();

        verify(kafkaTemplate).send(eq("notifications"), eq("ivanov"), any(NotificationEvent.class));
    }

    @Test
    @DisplayName("Insufficient funds — accounts-service returns 400 → InsufficientFundsException")
    void shouldThrowInsufficientFundsExceptionOn400() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/accounts/ivanov/withdraw"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Insufficient funds\"}")));

        CashOperationRequest request = new CashOperationRequest(10000L, CashAction.GET);

        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);

        StepVerifier.create(result)
                .expectError(InsufficientFundsException.class)
                .verify();
    }

    @Test
    @DisplayName("accounts-service unavailable (5xx) → CashOperationException")
    void shouldThrowCashOperationExceptionOn5xx() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/accounts/ivanov/deposit"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withBody("Internal Server Error")));

        CashOperationRequest request = new CashOperationRequest(500L, CashAction.PUT);

        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);

        StepVerifier.create(result)
                .expectErrorMatches(ex ->
                        ex instanceof CashOperationException &&
                        ex.getMessage().contains("Сервис счетов недоступен"))
                .verify();
    }

    @Test
    @DisplayName("Fallback when circuit breaker is open → CashOperationException")
    @SuppressWarnings("unchecked")
    void shouldReturnFallbackWhenCircuitBreakerIsOpen() {
        CashOperationRequest request = new CashOperationRequest(500L, CashAction.PUT);

        Mono<CashResponse> result = (Mono<CashResponse>) ReflectionTestUtils.invokeMethod(
                cashService,
                "fallbackCashOperation",
                "ivanov",
                request,
                new RuntimeException("Circuit breaker is open")
        );

        StepVerifier.create(result)
                .expectErrorMatches(ex ->
                        ex instanceof CashOperationException &&
                        ex.getMessage().contains("Сервис временно недоступен"))
                .verify();
    }

    @Test
    @DisplayName("Other 4xx (e.g. 403) from accounts-service → CashOperationException")
    void shouldThrowCashOperationExceptionOnOther4xx() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/accounts/ivanov/deposit"))
                .willReturn(aResponse()
                        .withStatus(403)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"Forbidden\"}")));

        CashOperationRequest request = new CashOperationRequest(500L, CashAction.PUT);

        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);

        StepVerifier.create(result)
                .expectError(CashOperationException.class)
                .verify();
    }

    @Test
    @DisplayName("Deposit info message contains amount value")
    void shouldIncludeAmountInDepositInfoMessage() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/accounts/ivanov/deposit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("1005000")));

        CashOperationRequest request = new CashOperationRequest(1000000L, CashAction.PUT);

        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.newBalance()).isEqualTo(1005000L);
                    assertThat(response.info()).contains("1000000");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Minimum valid amount (1L) is processed correctly")
    void shouldProcessMinimumValidAmount() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/accounts/ivanov/deposit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("5001")));

        CashOperationRequest request = new CashOperationRequest(1L, CashAction.PUT);

        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);

        StepVerifier.create(result)
                .assertNext(response -> assertThat(response.newBalance()).isEqualTo(5001L))
                .verifyComplete();
    }
}
