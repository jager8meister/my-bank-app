package ru.yandex.practicum.transfer.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler unit tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    // -------------------------------------------------------------------------
    // InsufficientFundsException → 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("InsufficientFundsException → HTTP 400 with message")
    void shouldReturn400_forInsufficientFundsException() {
        InsufficientFundsException ex = new InsufficientFundsException("Not enough money");

        StepVerifier.create(handler.handleInsufficientFundsException(ex))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("message")).isEqualTo("Not enough money");
                    assertThat(body.get("status")).isEqualTo(400);
                    assertThat(body.get("error")).isEqualTo("Bad Request");
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // InvalidTransferException → 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("InvalidTransferException → HTTP 400 with message")
    void shouldReturn400_forInvalidTransferException() {
        InvalidTransferException ex = new InvalidTransferException("Cannot transfer to yourself");

        StepVerifier.create(handler.handleInvalidTransferException(ex))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("message")).isEqualTo("Cannot transfer to yourself");
                    assertThat(body.get("status")).isEqualTo(400);
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // TransferException → 400
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("TransferException → HTTP 400 with message")
    void shouldReturn400_forTransferException() {
        TransferException ex = new TransferException("Transfer failed: timeout");

        StepVerifier.create(handler.handleTransferException(ex))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("message")).isEqualTo("Transfer failed: timeout");
                    assertThat(body.get("status")).isEqualTo(400);
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // UnauthorizedException → 401
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("UnauthorizedException → HTTP 401 with message")
    void shouldReturn401_forUnauthorizedException() {
        UnauthorizedException ex = new UnauthorizedException("Valid JWT authentication required");

        StepVerifier.create(handler.handleUnauthorizedException(ex))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("message")).isEqualTo("Valid JWT authentication required");
                    assertThat(body.get("status")).isEqualTo(401);
                    assertThat(body.get("error")).isEqualTo("Unauthorized");
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // ForbiddenException → 403
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ForbiddenException → HTTP 403 with message")
    void shouldReturn403_forForbiddenException() {
        ForbiddenException ex = new ForbiddenException("You can only transfer money from your own account");

        StepVerifier.create(handler.handleForbiddenException(ex))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("message")).isEqualTo("You can only transfer money from your own account");
                    assertThat(body.get("status")).isEqualTo(403);
                    assertThat(body.get("error")).isEqualTo("Forbidden");
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Generic Exception → 500
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Unexpected exception → HTTP 500")
    void shouldReturn500_forGenericException() {
        Exception ex = new RuntimeException("Something completely unexpected");

        StepVerifier.create(handler.handleGenericException(ex))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.get("status")).isEqualTo(500);
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // Error response always contains timestamp
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Error response always contains timestamp field")
    void shouldIncludeTimestamp_inAllErrorResponses() {
        InsufficientFundsException ex = new InsufficientFundsException("No money");

        StepVerifier.create(handler.handleInsufficientFundsException(ex))
                .assertNext(response -> {
                    Map<String, Object> body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body).containsKey("timestamp");
                    assertThat((Long) body.get("timestamp")).isPositive();
                })
                .verifyComplete();
    }
}
