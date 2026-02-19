package ru.yandex.practicum.notifications.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.notifications.config.TestSecurityConfig;
import ru.yandex.practicum.notifications.dto.NotificationRequestDto;
import ru.yandex.practicum.notifications.dto.NotificationResponseDto;
import ru.yandex.practicum.notifications.dto.NotificationType;
import ru.yandex.practicum.notifications.service.NotificationService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
@WebFluxTest(
        controllers = NotificationController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration.class
        }
)
@Import(TestSecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private NotificationService notificationService;
    @Test
    void shouldSendNotification() {
        NotificationRequestDto request = new NotificationRequestDto(
                "ivanov",
                "Test message",
                NotificationType.ACCOUNT_UPDATED
        );
        NotificationResponseDto response = new NotificationResponseDto(true, "Notification sent successfully");
        when(notificationService.sendNotification(any(NotificationRequestDto.class)))
                .thenReturn(Mono.just(response));
        webTestClient
                .post()
                .uri("/api/notifications")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Notification sent successfully");
    }
    @Test
    void shouldHandleTransferNotification() {
        NotificationRequestDto request = new NotificationRequestDto(
                "petrov",
                "Transfer received",
                NotificationType.TRANSFER_RECEIVED
        );
        NotificationResponseDto response = new NotificationResponseDto(true, "Notification sent successfully to petrov");
        when(notificationService.sendNotification(any(NotificationRequestDto.class)))
                .thenReturn(Mono.just(response));
        webTestClient
                .post()
                .uri("/api/notifications")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }
    @Test
    void shouldHandleBalanceLowNotification() {
        NotificationRequestDto request = new NotificationRequestDto(
                "sidorov",
                "Balance is low",
                NotificationType.BALANCE_LOW
        );
        NotificationResponseDto response = new NotificationResponseDto(true, "Notification sent");
        when(notificationService.sendNotification(any(NotificationRequestDto.class)))
                .thenReturn(Mono.just(response));
        webTestClient
                .post()
                .uri("/api/notifications")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }
}
