package ru.yandex.practicum.transfer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.transfer.client.NotificationClient;
import ru.yandex.practicum.transfer.dto.TransferRequest;
import ru.yandex.practicum.transfer.dto.TransferResponse;
import ru.yandex.practicum.transfer.exception.InsufficientFundsException;
import ru.yandex.practicum.transfer.exception.InvalidTransferException;
import ru.yandex.practicum.transfer.exception.TransferException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
@DisplayName("TransferService Unit Tests")
class TransferServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private NotificationClient notificationClient;
    private TransferService transferService;
    @BeforeEach
    void setUp() {
        transferService = new TransferService(webClient, notificationClient);
    }
    @Test
    @DisplayName("Should reject transfer with negative amount")
    void shouldRejectNegativeAmount() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", -100);
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(InvalidTransferException.class)
                .verify();
        verify(webClient, never()).post();
        verify(notificationClient, never()).sendTransferNotification(anyString(), anyString(), anyString());
    }
    @Test
    void shouldRejectZeroAmount() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 0);
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
        TransferRequest request = new TransferRequest("ivanov", "ivanov", 500);
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(InvalidTransferException.class)
                .verify();
        verify(webClient, never()).post();
        verify(notificationClient, never()).sendTransferNotification(anyString(), anyString(), anyString());
    }
    @Test
    void shouldCallWebClientForValidTransfer() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500);
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
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500);
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
        TransferRequest request = new TransferRequest("ivanov", "petrov", 10000);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(new RuntimeException("Insufficient funds")));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(InsufficientFundsException.class)
                .verify();
    }
    @Test
    void shouldTransferSuccessfullyAndSendNotifications() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500);
        TransferService.TransferResult transferResult =
                new TransferService.TransferResult(4500, 1500, "Иван Иванов", "Петр Петров");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TransferService.TransferResult.class))
                .thenReturn(Mono.just(transferResult));
        when(notificationClient.sendTransferNotification(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.message()).isEqualTo("Transfer successful");
                    assertThat(response.senderBalance()).isEqualTo(4500);
                    assertThat(response.recipientBalance()).isEqualTo(1500);
                })
                .verifyComplete();
        verify(notificationClient, times(2)).sendTransferNotification(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should continue when sender notification fails")
    void shouldContinueWhenSenderNotificationFails() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500);
        TransferService.TransferResult transferResult =
                new TransferService.TransferResult(4500, 1500, "Иван Иванов", "Петр Петров");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TransferService.TransferResult.class))
                .thenReturn(Mono.just(transferResult));
        when(notificationClient.sendTransferNotification(anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Notification service down")))
                .thenReturn(Mono.empty());
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.senderBalance()).isEqualTo(4500);
                    assertThat(response.recipientBalance()).isEqualTo(1500);
                })
                .verifyComplete();
        verify(notificationClient, times(2)).sendTransferNotification(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should continue when all notifications fail")
    void shouldContinueWhenAllNotificationsFail() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500);
        TransferService.TransferResult transferResult =
                new TransferService.TransferResult(4500, 1500, "Иван Иванов", "Петр Петров");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TransferService.TransferResult.class))
                .thenReturn(Mono.just(transferResult));
        when(notificationClient.sendTransferNotification(anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Notification service down")));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should transfer large amount successfully")
    void shouldTransferLargeAmountSuccessfully() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 1000000);
        TransferService.TransferResult transferResult =
                new TransferService.TransferResult(0, 1001000, "Иван Иванов", "Петр Петров");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TransferService.TransferResult.class))
                .thenReturn(Mono.just(transferResult));
        when(notificationClient.sendTransferNotification(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.senderBalance()).isEqualTo(0);
                    assertThat(response.recipientBalance()).isEqualTo(1001000);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should transfer minimum amount successfully")
    void shouldTransferMinimumAmountSuccessfully() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 1);
        TransferService.TransferResult transferResult =
                new TransferService.TransferResult(4999, 1001, "Иван Иванов", "Петр Петров");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TransferService.TransferResult.class))
                .thenReturn(Mono.just(transferResult));
        when(notificationClient.sendTransferNotification(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.senderBalance()).isEqualTo(4999);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle error with account not found message")
    void shouldHandleAccountNotFoundError() {
        TransferRequest request = new TransferRequest("unknown", "petrov", 500);
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
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500);
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
    @DisplayName("Should handle case insensitive insufficient funds error")
    void shouldHandleCaseInsensitiveInsufficientFundsError() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 10000);
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class)))
                .thenReturn(Mono.error(new RuntimeException("INSUFFICIENT FUNDS")));
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectError(InsufficientFundsException.class)
                .verify();
    }

    @Test
    @DisplayName("Should verify correct notification messages")
    void shouldVerifyCorrectNotificationMessages() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500);
        TransferService.TransferResult transferResult =
                new TransferService.TransferResult(4500, 1500, "Иван Иванов", "Петр Петров");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TransferService.TransferResult.class))
                .thenReturn(Mono.just(transferResult));
        when(notificationClient.sendTransferNotification(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        Mono<TransferResponse> responseMono = transferService.transfer(request);
        StepVerifier.create(responseMono)
                .expectNextCount(1)
                .verifyComplete();
        verify(notificationClient).sendTransferNotification(
                eq("ivanov"),
                contains("500"),
                eq("TRANSFER_SENT")
        );
        verify(notificationClient).sendTransferNotification(
                eq("petrov"),
                contains("500"),
                eq("TRANSFER_RECEIVED")
        );
    }
}
