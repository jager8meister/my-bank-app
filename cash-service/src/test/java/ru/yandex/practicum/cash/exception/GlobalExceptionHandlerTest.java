package ru.yandex.practicum.cash.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("InsufficientFundsException → 400 Bad Request")
    void shouldReturn400ForInsufficientFundsException() {
        InsufficientFundsException ex = new InsufficientFundsException("Недостаточно средств на счету");

        Mono<ResponseEntity<Map<String, Object>>> result = handler.handleInsufficientFundsException(ex);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().get("status")).isEqualTo(400);
                    assertThat(response.getBody().get("message")).isEqualTo("Недостаточно средств на счету");
                    assertThat(response.getBody().get("error")).isEqualTo("Bad Request");
                    assertThat(response.getBody()).containsKey("timestamp");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("CashOperationException → 400 Bad Request")
    void shouldReturn400ForCashOperationException() {
        CashOperationException ex = new CashOperationException("Сервис счетов недоступен: 500 INTERNAL_SERVER_ERROR");

        Mono<ResponseEntity<Map<String, Object>>> result = handler.handleCashOperationException(ex);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().get("status")).isEqualTo(400);
                    assertThat(response.getBody().get("message"))
                            .isEqualTo("Сервис счетов недоступен: 500 INTERNAL_SERVER_ERROR");
                    assertThat(response.getBody().get("error")).isEqualTo("Bad Request");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("CashOperationException with cause → 400 Bad Request")
    void shouldReturn400ForCashOperationExceptionWithCause() {
        CashOperationException ex = new CashOperationException(
                "Ошибка операции", new RuntimeException("Connection refused"));

        Mono<ResponseEntity<Map<String, Object>>> result = handler.handleCashOperationException(ex);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(response.getBody().get("message")).isEqualTo("Ошибка операции");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("UnauthorizedException → 401 Unauthorized")
    void shouldReturn401ForUnauthorizedException() {
        UnauthorizedException ex = new UnauthorizedException("Valid JWT authentication required");

        Mono<ResponseEntity<Map<String, Object>>> result = handler.handleUnauthorizedException(ex);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().get("status")).isEqualTo(401);
                    assertThat(response.getBody().get("message")).isEqualTo("Valid JWT authentication required");
                    assertThat(response.getBody().get("error")).isEqualTo("Unauthorized");
                    assertThat(response.getBody()).containsKey("timestamp");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("ForbiddenException → 403 Forbidden")
    void shouldReturn403ForForbiddenException() {
        ForbiddenException ex = new ForbiddenException("You can only perform operations on your own account");

        Mono<ResponseEntity<Map<String, Object>>> result = handler.handleForbiddenException(ex);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(response.getBody()).isNotNull();
                    assertThat(response.getBody().get("status")).isEqualTo(403);
                    assertThat(response.getBody().get("message"))
                            .isEqualTo("You can only perform operations on your own account");
                    assertThat(response.getBody().get("error")).isEqualTo("Forbidden");
                    assertThat(response.getBody()).containsKey("timestamp");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Error response always contains timestamp field")
    void shouldAlwaysIncludeTimestampInErrorResponse() {
        CashOperationException ex = new CashOperationException("test error");

        Mono<ResponseEntity<Map<String, Object>>> result = handler.handleCashOperationException(ex);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.getBody()).containsKey("timestamp");
                    Object timestamp = response.getBody().get("timestamp");
                    assertThat(timestamp).isInstanceOf(Long.class);
                    assertThat((Long) timestamp).isPositive();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Error response contains all required fields: error, message, status, timestamp")
    void shouldContainAllRequiredFieldsInErrorResponse() {
        InsufficientFundsException ex = new InsufficientFundsException("Insufficient funds");

        Mono<ResponseEntity<Map<String, Object>>> result = handler.handleInsufficientFundsException(ex);

        StepVerifier.create(result)
                .assertNext(response -> {
                    Map<String, Object> body = response.getBody();
                    assertThat(body).containsKeys("error", "message", "status", "timestamp");
                })
                .verifyComplete();
    }
}
