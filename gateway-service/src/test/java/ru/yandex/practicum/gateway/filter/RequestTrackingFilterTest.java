package ru.yandex.practicum.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RequestTrackingFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private RequestTrackingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestTrackingFilter();
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void filter_addsXRequestId_whenHeaderAbsent() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(exchangeCaptor.capture());

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        String requestId = capturedExchange.getRequest().getHeaders().getFirst("X-Request-ID");
        assertThat(requestId).isNotNull().isNotEmpty();
        // Should be a valid UUID format
        assertThat(requestId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void filter_doesNotOverwriteXRequestId_whenHeaderPresent() {
        String existingRequestId = "my-existing-request-id-12345";
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-Request-ID", existingRequestId)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(exchangeCaptor.capture());

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        String requestId = capturedExchange.getRequest().getHeaders().getFirst("X-Request-ID");
        assertThat(requestId).isEqualTo(existingRequestId);
    }

    @Test
    void filter_addsXRequestIdToResponse() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        String responseRequestId = exchange.getResponse().getHeaders().getFirst("X-Request-ID");
        assertThat(responseRequestId).isNotNull().isNotEmpty();
    }

    @Test
    void filter_addsXRequestTimeHeader() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        ArgumentCaptor<ServerWebExchange> exchangeCaptor = ArgumentCaptor.forClass(ServerWebExchange.class);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(exchangeCaptor.capture());

        ServerWebExchange capturedExchange = exchangeCaptor.getValue();
        String requestTime = capturedExchange.getRequest().getHeaders().getFirst("X-Request-Time");
        assertThat(requestTime).isNotNull().isNotEmpty();
        // Should be a valid timestamp (parseable as long)
        assertThat(Long.parseLong(requestTime)).isPositive();
    }

    @Test
    void filter_callsChain() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, times(1)).filter(any(ServerWebExchange.class));
    }

    @Test
    void getOrder_returnsHighestPrecedence() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }
}
