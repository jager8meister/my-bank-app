package ru.yandex.practicum.notifications.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.notifications.dto.NotificationRequestDto;
import ru.yandex.practicum.notifications.dto.NotificationResponseDto;
import ru.yandex.practicum.notifications.dto.NotificationType;
import static org.assertj.core.api.Assertions.assertThat;

class NotificationServiceTest {
    private NotificationService notificationService;
    @BeforeEach
    void setUp() {
        notificationService = new NotificationService();
    }
    @Test
    void shouldSendNotificationSuccessfully() {
        NotificationRequestDto request = new NotificationRequestDto(
                "ivanov",
                "Test notification message",
                NotificationType.ACCOUNT_UPDATED
        );
        Mono<NotificationResponseDto> responseMono = notificationService.sendNotification(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.message()).contains("Notification sent successfully");
                    assertThat(response.message()).contains("ivanov");
                })
                .verifyComplete();
    }
    @Test
    void shouldHandleTransferSentNotification() {
        NotificationRequestDto request = new NotificationRequestDto(
                "petrov",
                "You sent 500 rubles",
                NotificationType.TRANSFER_SENT
        );
        Mono<NotificationResponseDto> responseMono = notificationService.sendNotification(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.message()).contains("petrov");
                })
                .verifyComplete();
    }
    @Test
    void shouldHandleTransferReceivedNotification() {
        NotificationRequestDto request = new NotificationRequestDto(
                "sidorov",
                "You received 1000 rubles",
                NotificationType.TRANSFER_RECEIVED
        );
        Mono<NotificationResponseDto> responseMono = notificationService.sendNotification(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.message()).contains("sidorov");
                })
                .verifyComplete();
    }
    @Test
    void shouldHandleAccountUpdatedNotification() {
        NotificationRequestDto request = new NotificationRequestDto(
                "ivanov",
                "Account information updated",
                NotificationType.ACCOUNT_UPDATED
        );
        Mono<NotificationResponseDto> responseMono = notificationService.sendNotification(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> assertThat(response.success()).isTrue())
                .verifyComplete();
    }
    @Test
    void shouldHandleBalanceLowNotification() {
        NotificationRequestDto request = new NotificationRequestDto(
                "petrov",
                "Your balance is low",
                NotificationType.BALANCE_LOW
        );
        Mono<NotificationResponseDto> responseMono = notificationService.sendNotification(request);
        StepVerifier.create(responseMono)
                .assertNext(response -> assertThat(response.success()).isTrue())
                .verifyComplete();
    }
}
