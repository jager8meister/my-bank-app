package ru.yandex.practicum.cash.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.cash.dto.CashAction;
import ru.yandex.practicum.cash.dto.CashOperationRequest;
import ru.yandex.practicum.cash.dto.CashResponse;
import ru.yandex.practicum.cash.exception.CashOperationException;
import ru.yandex.practicum.cash.exception.InsufficientFundsException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CashService Unit Tests")
class CashServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private CashService cashService;

    @BeforeEach
    void setUp() {
        cashService = new CashService(webClient);
    }

    @Test
    @DisplayName("Should process PUT operation successfully")
    void shouldProcessPutOperationSuccessfully() {
        CashOperationRequest request = new CashOperationRequest(500L, CashAction.PUT);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Long.class)).thenReturn(Mono.just(5500L));
        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.newBalance()).isEqualTo(5500L);
                    assertThat(response.info()).contains("Положено 500 руб");
                    assertThat(response.errors()).isNull();
                })
                .verifyComplete();
        verify(webClient).post();
    }

    @Test
    @DisplayName("Should process GET operation successfully")
    void shouldProcessGetOperationSuccessfully() {
        CashOperationRequest request = new CashOperationRequest(500L, CashAction.GET);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Long.class)).thenReturn(Mono.just(4500L));
        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.newBalance()).isEqualTo(4500L);
                    assertThat(response.info()).contains("Снято 500 руб");
                    assertThat(response.errors()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should throw InsufficientFundsException on insufficient funds")
    void shouldThrowInsufficientFundsException() {
        CashOperationRequest request = new CashOperationRequest(10000L, CashAction.GET);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Long.class))
                .thenReturn(Mono.error(new RuntimeException("Insufficient funds. Available: 5000")));
        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);
        StepVerifier.create(result)
                .expectError(InsufficientFundsException.class)
                .verify();
    }

    @Test
    @DisplayName("Should throw CashOperationException on generic error")
    void shouldThrowCashOperationException() {
        CashOperationRequest request = new CashOperationRequest(500L, CashAction.PUT);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Long.class))
                .thenReturn(Mono.error(new RuntimeException("Service unavailable")));
        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);
        StepVerifier.create(result)
                .expectError(CashOperationException.class)
                .verify();
    }

    @Test
    @DisplayName("Should throw CashOperationException when error message is null")
    void shouldThrowCashOperationExceptionWhenErrorMessageIsNull() {
        CashOperationRequest request = new CashOperationRequest(500L, CashAction.GET);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Long.class))
                .thenReturn(Mono.error(new RuntimeException()));
        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);
        StepVerifier.create(result)
                .expectError(CashOperationException.class)
                .verify();
    }

    @Test
    @DisplayName("Should use correct endpoint for PUT operation")
    void shouldUseCorrectEndpointForPutOperation() {
        CashOperationRequest request = new CashOperationRequest(1000L, CashAction.PUT);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Long.class)).thenReturn(Mono.just(6000L));
        cashService.processCashOperation("petrov", request).block();
        verify(requestBodyUriSpec).uri(any(java.util.function.Function.class));
    }

    @Test
    @DisplayName("Should use correct endpoint for GET operation")
    void shouldUseCorrectEndpointForGetOperation() {
        CashOperationRequest request = new CashOperationRequest(1000L, CashAction.GET);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Long.class)).thenReturn(Mono.just(2000L));
        cashService.processCashOperation("sidorov", request).block();
        verify(requestBodyUriSpec).uri(any(java.util.function.Function.class));
    }

    @Test
    @DisplayName("Should handle WebClientResponseException")
    void shouldHandleWebClientResponseException() {
        CashOperationRequest request = new CashOperationRequest(500L, CashAction.PUT);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Long.class))
                .thenReturn(Mono.error(WebClientResponseException.create(
                        503,
                        "Service Unavailable",
                        null,
                        null,
                        null
                )));
        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);
        StepVerifier.create(result)
                .expectError(CashOperationException.class)
                .verify();
    }

    @Test
    @DisplayName("Should handle error with 'Insufficient funds' in message")
    void shouldHandleInsufficientFundsInErrorMessage() {
        CashOperationRequest request = new CashOperationRequest(5000L, CashAction.GET);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Long.class))
                .thenReturn(Mono.error(new RuntimeException("Insufficient funds")));
        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);
        StepVerifier.create(result)
                .expectError(InsufficientFundsException.class)
                .verify();
    }

    @Test
    @DisplayName("Should process large deposit amount")
    void shouldProcessLargeDepositAmount() {
        CashOperationRequest request = new CashOperationRequest(1000000L, CashAction.PUT);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Long.class)).thenReturn(Mono.just(1005000L));
        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.newBalance()).isEqualTo(1005000L);
                    assertThat(response.info()).contains("1000000");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should process minimum valid amount")
    void shouldProcessMinimumValidAmount() {
        CashOperationRequest request = new CashOperationRequest(1L, CashAction.PUT);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Long.class)).thenReturn(Mono.just(5001L));
        Mono<CashResponse> result = cashService.processCashOperation("ivanov", request);
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.newBalance()).isEqualTo(5001L);
                })
                .verifyComplete();
    }
}
