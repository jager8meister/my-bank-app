package ru.yandex.practicum.transfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.transfer.dto.NotificationEvent;
import ru.yandex.practicum.transfer.dto.TransferRequest;
import ru.yandex.practicum.transfer.dto.TransferResponse;
import ru.yandex.practicum.transfer.exception.InsufficientFundsException;
import ru.yandex.practicum.transfer.exception.InvalidTransferException;
import ru.yandex.practicum.transfer.exception.TransferException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransferService Unit Tests")
class TransferServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(webClient, objectMapper, kafkaTemplate);
    }

    @Test
    @DisplayName("Should reject transfer with negative amount")
    void shouldRejectNegativeAmount() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", -100L);
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(InvalidTransferException.class)
                .verify();
        verify(webClient, never()).post();
    }

    @Test
    void shouldRejectZeroAmount() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 0L);
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(InvalidTransferException.class)
                .verify();
        verify(webClient, never()).post();
    }

    @Test
    void shouldRejectNullAmount() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", null);
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(InvalidTransferException.class)
                .verify();
        verify(webClient, never()).post();
    }

    @Test
    void shouldRejectTransferToSelf() {
        TransferRequest request = new TransferRequest("ivanov", "ivanov", 500L);
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(InvalidTransferException.class)
                .verify();
        verify(webClient, never()).post();
    }

    @Test
    void shouldCallWebClientForValidTransfer() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class))).thenReturn(Mono.error(new RuntimeException("Test error")));
        try {
            transferService.transfer(request).block();
        } catch (Exception e) {
        }
        verify(webClient).post();
        verify(requestBodyUriSpec).uri(any(java.util.function.Function.class));
        verify(requestBodySpec).retrieve();
        verify(responseSpec).bodyToMono(any(Class.class));
    }

    @Test
    void shouldHandleWebClientError() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(new RuntimeException("Service unavailable")));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(TransferException.class)
                .verify();
    }

    @Test
    void shouldHandleInsufficientFundsError() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 10000L);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        org.springframework.web.reactive.function.client.WebClientResponseException ex400 =
                org.springframework.web.reactive.function.client.WebClientResponseException.create(
                        400, "Bad Request", null,
                        "{\"message\":\"Insufficient funds\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.charset.StandardCharsets.UTF_8);
        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(ex400));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(InsufficientFundsException.class)
                .verify();
    }

    @Test
    void shouldTransferSuccessfully() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);
        TransferService.TransferResult transferResult =
                new TransferService.TransferResult(4500L, 1500L, "Иван Иванов", "Петр Петров");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TransferService.TransferResult.class))
                .thenReturn(Mono.just(transferResult));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.message()).isEqualTo("Transfer successful");
                    assertThat(response.senderBalance()).isEqualTo(4500L);
                    assertThat(response.recipientBalance()).isEqualTo(1500L);
                })
                .verifyComplete();

        verify(kafkaTemplate, times(2)).send(eq("notifications"), anyString(), any(NotificationEvent.class));
    }

    @Test
    @DisplayName("Should transfer large amount successfully")
    void shouldTransferLargeAmountSuccessfully() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 1000000L);
        TransferService.TransferResult transferResult =
                new TransferService.TransferResult(0L, 1001000L, "Иван Иванов", "Петр Петров");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TransferService.TransferResult.class))
                .thenReturn(Mono.just(transferResult));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.senderBalance()).isEqualTo(0L);
                    assertThat(response.recipientBalance()).isEqualTo(1001000L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should transfer minimum amount successfully")
    void shouldTransferMinimumAmountSuccessfully() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 1L);
        TransferService.TransferResult transferResult =
                new TransferService.TransferResult(4999L, 1001L, "Иван Иванов", "Петр Петров");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TransferService.TransferResult.class))
                .thenReturn(Mono.just(transferResult));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.senderBalance()).isEqualTo(4999L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle error with account not found message")
    void shouldHandleAccountNotFoundError() {
        TransferRequest request = new TransferRequest("unknown", "petrov", 500L);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(new RuntimeException("Account not found")));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(TransferException.class)
                .verify();
    }

    @Test
    @DisplayName("Should handle null error message")
    void shouldHandleNullErrorMessage() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(new RuntimeException()));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(TransferException.class)
                .verify();
    }

    @Test
    @DisplayName("Should handle 400 response as insufficient funds error")
    void shouldHandleCaseInsensitiveInsufficientFundsError() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 10000L);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        org.springframework.web.reactive.function.client.WebClientResponseException ex400 =
                org.springframework.web.reactive.function.client.WebClientResponseException.create(
                        400, "Bad Request", null,
                        "{\"message\":\"INSUFFICIENT FUNDS\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        java.nio.charset.StandardCharsets.UTF_8);
        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(ex400));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(InsufficientFundsException.class)
                .verify();
    }

    @Test
    @DisplayName("Should return correct balances on transfer")
    void shouldReturnCorrectBalancesOnTransfer() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);
        TransferService.TransferResult transferResult =
                new TransferService.TransferResult(4500L, 1500L, "Иван Иванов", "Петр Петров");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TransferService.TransferResult.class))
                .thenReturn(Mono.just(transferResult));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.senderBalance()).isEqualTo(4500L);
                    assertThat(response.recipientBalance()).isEqualTo(1500L);
                })
                .verifyComplete();
    }
}
