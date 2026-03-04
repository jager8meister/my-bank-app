package ru.yandex.practicum.notifications.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.notifications.config.SecurityTestConfigWithJwt;
import ru.yandex.practicum.notifications.controller.NotificationController;
import ru.yandex.practicum.notifications.dto.NotificationRequestDto;
import ru.yandex.practicum.notifications.dto.NotificationType;
import ru.yandex.practicum.notifications.service.NotificationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

@WebFluxTest(controllers = NotificationController.class)
@Import({SecurityTestConfigWithJwt.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private NotificationService notificationService;

    /**
     * WebExchangeBindException is triggered by @Valid + @NotBlank when the field is blank.
     * GlobalExceptionHandler.handleValidation() catches it and returns 400 with {"error": "<field>: <msg>"}.
     */
    @Test
    void handleValidation_whenRequestBodyInvalid_returns400WithErrorField() {
        NotificationRequestDto invalidRequest = new NotificationRequestDto(
                "",              // @NotBlank violation on recipient
                "Some message",
                NotificationType.ACCOUNT_UPDATED
        );

        webTestClient
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").exists()
                .jsonPath("$.error").isNotEmpty();
    }

    @Test
    void handleValidation_whenBlankMessage_returns400WithErrorField() {
        NotificationRequestDto invalidRequest = new NotificationRequestDto(
                "ivanov",
                "",              // @NotBlank violation on message
                NotificationType.TRANSFER_SENT
        );

        webTestClient
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidRequest)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").exists()
                .jsonPath("$.error").isNotEmpty();
    }

    /**
     * General Exception -> 500 with {"error": "Internal server error"}.
     * GlobalExceptionHandler.handleGeneral() catches RuntimeException thrown by the service.
     */
    @Test
    void handleGeneral_whenServiceThrowsException_returns500WithErrorField() {
        when(notificationService.sendNotification(any(NotificationRequestDto.class)))
                .thenReturn(Mono.error(new RuntimeException("Unexpected failure")));

        NotificationRequestDto validRequest = new NotificationRequestDto(
                "petrov",
                "Test message",
                NotificationType.BALANCE_LOW
        );

        webTestClient
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validRequest)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Internal server error");
    }
}
