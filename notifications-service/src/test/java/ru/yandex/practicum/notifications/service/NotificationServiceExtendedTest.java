package ru.yandex.practicum.notifications.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.notifications.dto.NotificationRequestDto;
import ru.yandex.practicum.notifications.dto.NotificationResponseDto;
import ru.yandex.practicum.notifications.dto.NotificationType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceExtendedTest {

    @Spy
    private NotificationService notificationService;

    @Test
    void sendNotification_withValidRequest_returnsSuccessTrue() {
        NotificationRequestDto request = new NotificationRequestDto(
                "ivanov",
                "Your transfer is complete",
                NotificationType.TRANSFER_SENT
        );

        Mono<NotificationResponseDto> result = notificationService.sendNotification(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.message()).isNotNull();
                    assertThat(response.message()).contains("ivanov");
                })
                .verifyComplete();
    }

    @Test
    void sendNotification_withValidRequest_messageContainsSuccessfullySent() {
        NotificationRequestDto request = new NotificationRequestDto(
                "petrov",
                "Balance is below threshold",
                NotificationType.BALANCE_LOW
        );

        Mono<NotificationResponseDto> result = notificationService.sendNotification(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.success()).isTrue();
                    assertThat(response.message()).contains("Notification sent successfully");
                })
                .verifyComplete();
    }

    @Test
    void sendNotification_isCalledOnce_verifiedViaSpy() {
        NotificationRequestDto request = new NotificationRequestDto(
                "sidorov",
                "Account updated",
                NotificationType.ACCOUNT_UPDATED
        );

        Mono<NotificationResponseDto> result = notificationService.sendNotification(request);

        StepVerifier.create(result)
                .assertNext(response -> assertThat(response.success()).isTrue())
                .verifyComplete();

        verify(notificationService, times(1)).sendNotification(any(NotificationRequestDto.class));
    }

    @Test
    void sendNotification_allNotificationTypes_returnSuccess() {
        for (NotificationType type : NotificationType.values()) {
            NotificationRequestDto request = new NotificationRequestDto(
                    "user",
                    "Test message for " + type,
                    type
            );

            Mono<NotificationResponseDto> result = notificationService.sendNotification(request);

            StepVerifier.create(result)
                    .assertNext(response -> assertThat(response.success()).isTrue())
                    .verifyComplete();
        }
    }
}
