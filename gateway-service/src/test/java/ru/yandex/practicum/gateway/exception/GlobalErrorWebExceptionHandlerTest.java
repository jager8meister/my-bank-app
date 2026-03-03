package ru.yandex.practicum.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GlobalErrorWebExceptionHandlerTest {

    private GlobalErrorWebExceptionHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        handler = new GlobalErrorWebExceptionHandler();
        objectMapper = new ObjectMapper();
    }

    @Test
    void handle_returns503_forNotFoundException() throws Exception {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/accounts")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        NotFoundException ex = NotFoundException.create(false, "Service not found");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    void handle_returns400_forIllegalArgumentException() throws Exception {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        IllegalArgumentException ex = new IllegalArgumentException("Bad argument");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void handle_returns500_forGenericException() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RuntimeException ex = new RuntimeException("Something went wrong");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void handle_returnsResponseStatusException_statusCode() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void handle_returnsJsonWithErrorField() throws Exception {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/accounts")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RuntimeException ex = new RuntimeException("Test error");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_JSON);

        String body = exchange.getResponse().getBodyAsString().block();
        assertThat(body).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = objectMapper.readValue(body, Map.class);
        assertThat(responseBody).containsKey("error");
        assertThat(responseBody).containsKey("status");
        assertThat(responseBody).containsKey("message");
        assertThat(responseBody).containsKey("timestamp");
        assertThat(responseBody).containsKey("path");
    }

    @Test
    void handle_setsPathFromRequest() throws Exception {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/accounts/123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RuntimeException ex = new RuntimeException("error");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        String body = exchange.getResponse().getBodyAsString().block();
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = objectMapper.readValue(body, Map.class);
        assertThat(responseBody.get("path")).isEqualTo("/api/accounts/123");
    }

    @Test
    void handle_usesGatewayError_whenMessageIsNull() throws Exception {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        RuntimeException ex = new RuntimeException((String) null);

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        String body = exchange.getResponse().getBodyAsString().block();
        @SuppressWarnings("unchecked")
        Map<String, Object> responseBody = objectMapper.readValue(body, Map.class);
        assertThat(responseBody.get("message")).isEqualTo("Gateway Error");
    }

    @Test
    void handle_returnsMonoError_whenResponseAlreadyCommitted() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        exchange.getResponse().setComplete().block();

        RuntimeException ex = new RuntimeException("Already committed");

        StepVerifier.create(handler.handle(exchange, ex))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void handle_returns404_forResponseStatusException404() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/unknown")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");

        StepVerifier.create(handler.handle(exchange, ex))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
