package ru.yandex.practicum.transfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.yandex.practicum.transfer.dto.NotificationEvent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.transfer.dto.TransferRequest;
import ru.yandex.practicum.transfer.dto.TransferResponse;
import ru.yandex.practicum.transfer.exception.InsufficientFundsException;
import ru.yandex.practicum.transfer.exception.InvalidTransferException;
import ru.yandex.practicum.transfer.exception.TransferException;

import java.nio.charset.StandardCharsets;

import io.micrometer.core.instrument.Counter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TransferService WebClient HTTP response mapping tests")
class TransferServiceWebClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Mock
    private MeterRegistry meterRegistry;

    private TransferService transferService;

    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    private WebClient.RequestBodySpec requestBodySpec;

    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        Counter mockCounter = mock(Counter.class);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(mockCounter);
        transferService = new TransferService(webClient, objectMapper, kafkaTemplate, meterRegistry);
        requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        requestBodySpec = mock(WebClient.RequestBodySpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    @DisplayName("Successful transfer: accounts-service returns 200 with balances")
    void shouldTransferSuccessfully_whenAccountsServiceReturns200() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);
        TransferService.TransferResult result =
                new TransferService.TransferResult(4500L, 1500L, "Ivan", "Petr");

        when(responseSpec.bodyToMono(TransferService.TransferResult.class))
                .thenReturn(Mono.just(result));

        StepVerifier.create(transferService.transfer(request))
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.message()).isEqualTo("Transfer successful");
                    assertThat(response.senderBalance()).isEqualTo(4500L);
                    assertThat(response.recipientBalance()).isEqualTo(1500L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("HTTP 400 from accounts-service → InsufficientFundsException")
    void shouldThrowInsufficientFundsException_whenAccountsServiceReturns400() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 99999L);

        WebClientResponseException ex400 = WebClientResponseException.create(
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                null,
                "{\"message\":\"Insufficient funds\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(ex400));

        StepVerifier.create(transferService.transfer(request))
                .expectError(InsufficientFundsException.class)
                .verify();
    }

    @Test
    @DisplayName("HTTP 404 from accounts-service → InvalidTransferException")
    void shouldThrowInvalidTransferException_whenAccountsServiceReturns404() {
        TransferRequest request = new TransferRequest("ivanov", "nobody", 500L);

        WebClientResponseException ex404 = WebClientResponseException.create(
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                null,
                "{\"message\":\"Recipient account not found\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(ex404));

        StepVerifier.create(transferService.transfer(request))
                .expectError(InvalidTransferException.class)
                .verify();
    }

    @Test
    @DisplayName("HTTP 500 from accounts-service → TransferException")
    void shouldThrowTransferException_whenAccountsServiceReturns500() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);

        WebClientResponseException ex500 = WebClientResponseException.create(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                null,
                "{\"message\":\"Service unavailable\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(ex500));

        StepVerifier.create(transferService.transfer(request))
                .expectError(TransferException.class)
                .verify();
    }

    @Test
    @DisplayName("HTTP 503 from accounts-service → TransferException")
    void shouldThrowTransferException_whenAccountsServiceReturns503() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);

        WebClientResponseException ex503 = WebClientResponseException.create(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Service Unavailable",
                null,
                "{}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
        );

        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(ex503));

        StepVerifier.create(transferService.transfer(request))
                .expectError(TransferException.class)
                .verify();
    }

    @Test
    @DisplayName("Transfer to self → InvalidTransferException (no WebClient call)")
    void shouldRejectTransferToSelf() {
        TransferRequest request = new TransferRequest("ivanov", "ivanov", 100L);

        StepVerifier.create(transferService.transfer(request))
                .expectErrorMatches(e ->
                        e instanceof InvalidTransferException &&
                        e.getMessage().contains("yourself"))
                .verify();
    }

    @Test
    @DisplayName("Negative amount → InvalidTransferException (no WebClient call)")
    void shouldRejectNegativeAmount_noWebClientCall() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", -1L);

        StepVerifier.create(transferService.transfer(request))
                .expectError(InvalidTransferException.class)
                .verify();
    }

    @Test
    @DisplayName("Zero amount → InvalidTransferException (no WebClient call)")
    void shouldRejectZeroAmount_noWebClientCall() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 0L);

        StepVerifier.create(transferService.transfer(request))
                .expectError(InvalidTransferException.class)
                .verify();
    }

    @Test
    @DisplayName("Null amount → InvalidTransferException (no WebClient call)")
    void shouldRejectNullAmount_noWebClientCall() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", null);

        StepVerifier.create(transferService.transfer(request))
                .expectError(InvalidTransferException.class)
                .verify();
    }

    @Test
    @DisplayName("Generic connection exception → TransferException")
    void shouldWrapGenericException_asTransferException() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);

        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(new RuntimeException("Connection refused")));

        StepVerifier.create(transferService.transfer(request))
                .expectError(TransferException.class)
                .verify();
    }
}
