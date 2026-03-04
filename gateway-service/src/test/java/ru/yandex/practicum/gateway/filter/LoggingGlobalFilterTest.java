package ru.yandex.practicum.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoggingGlobalFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private LoggingGlobalFilter filter;

    @BeforeEach
    void setUp() {
        filter = new LoggingGlobalFilter();
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void filter_doesNotBlockRequest_chainFilterIsCalled() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/api/accounts")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, times(1)).filter(exchange);
    }

    @Test
    void filter_doesNotThrowException() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();
    }

    @Test
    void filter_completesSuccessfully_whenChainEmitsEmpty() {
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerHttpRequest request = MockServerHttpRequest
                .post("http://localhost/api/cash/deposit")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();
    }

    @Test
    void filter_propagatesChainError() {
        RuntimeException error = new RuntimeException("downstream error");
        when(chain.filter(any())).thenReturn(Mono.error(error));

        MockServerHttpRequest request = MockServerHttpRequest
                .get("http://localhost/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void getOrder_returnsLowestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE);
    }
}
