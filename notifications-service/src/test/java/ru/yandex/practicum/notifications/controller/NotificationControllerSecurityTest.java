package ru.yandex.practicum.notifications.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.notifications.config.SecurityTestConfigWithJwt;
import ru.yandex.practicum.notifications.dto.NotificationRequestDto;
import ru.yandex.practicum.notifications.dto.NotificationResponseDto;
import ru.yandex.practicum.notifications.dto.NotificationType;
import ru.yandex.practicum.notifications.exception.GlobalExceptionHandler;
import ru.yandex.practicum.notifications.service.NotificationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@WebFluxTest(controllers = NotificationController.class)
@Import({SecurityTestConfigWithJwt.class, GlobalExceptionHandler.class})
class NotificationControllerSecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private NotificationService notificationService;

    private static final NotificationRequestDto VALID_REQUEST = new NotificationRequestDto(
            "ivanov",
            "Test notification",
            NotificationType.TRANSFER_SENT
    );

    @Test
    void postNotification_withValidJwtAndCorrectScope_returns200() {
        NotificationResponseDto response = new NotificationResponseDto(
                true,
                "Notification sent successfully to ivanov"
        );
        when(notificationService.sendNotification(any(NotificationRequestDto.class)))
                .thenReturn(Mono.just(response));

        webTestClient
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(VALID_REQUEST)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Notification sent successfully to ivanov");
    }

    @Test
    void postNotification_withoutAuthorization_returns401() {
        webTestClient
                .post()
                .uri("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(VALID_REQUEST)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void postNotification_withJwtButWrongScope_returns403() {
        webTestClient
                .mutateWith(mockJwt())
                .post()
                .uri("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(VALID_REQUEST)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void postNotification_withInvalidBody_emptyRecipient_returns400() {
        NotificationRequestDto invalidRequest = new NotificationRequestDto(
                "",
                "Some message",
                NotificationType.ACCOUNT_UPDATED
        );
        when(notificationService.sendNotification(any(NotificationRequestDto.class)))
                .thenReturn(Mono.empty());

        webTestClient
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").exists();
    }

    @Test
    void postNotification_withInvalidBody_emptyMessage_returns400() {
        NotificationRequestDto invalidRequest = new NotificationRequestDto(
                "ivanov",
                "",
                NotificationType.TRANSFER_RECEIVED
        );
        when(notificationService.sendNotification(any(NotificationRequestDto.class)))
                .thenReturn(Mono.empty());

        webTestClient
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").exists();
    }

    @Test
    void postNotification_withInvalidBody_nullType_returns400() {
        String jsonBody = "{\"recipient\":\"ivanov\",\"message\":\"Test\",\"type\":null}";

        when(notificationService.sendNotification(any(NotificationRequestDto.class)))
                .thenReturn(Mono.empty());

        webTestClient
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").exists();
    }
}
