package ru.yandex.practicum.transfer.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class NotificationClientTest {

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;
    private NotificationClient notificationClient;
    @BeforeEach
    void setUp() {
        notificationClient = new NotificationClient(webClient);
    }
    @Test
    void shouldSendTransferNotificationSuccessfully() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class)).thenReturn(Mono.empty());
        Mono<Void> result = notificationClient.sendTransferNotification(
                "ivanov",
                "Transferred 100 to petrov",
                "TRANSFER_SENT"
        );
        StepVerifier.create(result)
                .verifyComplete();
    }
    @Test
    void shouldHandleNotificationError() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Void.class))
                .thenReturn(Mono.error(new RuntimeException("Notification service down")));
        Mono<Void> result = notificationClient.sendTransferNotification(
                "ivanov",
                "Transferred 100 to petrov",
                "TRANSFER_SENT"
        );
        StepVerifier.create(result)
                .verifyComplete();
    }
}
