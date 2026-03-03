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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitingFilterTest {

    @Mock
    private GatewayFilterChain chain;

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void filter_allowsRequest_whenLimitNotExceeded() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .remoteAddress(new java.net.InetSocketAddress("127.0.0.1", 80))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, times(1)).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void filter_returns429_whenLimitExceeded() throws Exception {
        Field countersField = RateLimitingFilter.class.getDeclaredField("requestCounters");
        countersField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> counters = (Map<String, Object>) countersField.get(filter);

        Class<?> counterClass = Class.forName(
                "ru.yandex.practicum.gateway.filter.RateLimitingFilter$RequestCounter");
        Constructor<?> ctor = counterClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object counter = ctor.newInstance();

        Field countField = counterClass.getDeclaredField("count");
        countField.setAccessible(true);
        AtomicInteger atomicCount = (AtomicInteger) countField.get(counter);
        atomicCount.set(100);

        counters.put("192.168.1.1", counter);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-Forwarded-For", "192.168.1.1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
        verify(chain, times(0)).filter(any());
    }

    @Test
    void filter_resetsLimit_afterTimeWindowExpires() throws Exception {
        Field countersField = RateLimitingFilter.class.getDeclaredField("requestCounters");
        countersField.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> counters = (Map<String, Object>) countersField.get(filter);

        Class<?> counterClass = Class.forName(
                "ru.yandex.practicum.gateway.filter.RateLimitingFilter$RequestCounter");
        Constructor<?> ctor = counterClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object counter = ctor.newInstance();

        Field countField = counterClass.getDeclaredField("count");
        countField.setAccessible(true);
        AtomicInteger atomicCount = (AtomicInteger) countField.get(counter);
        atomicCount.set(100);

        Field windowStartField = counterClass.getDeclaredField("windowStart");
        windowStartField.setAccessible(true);
        windowStartField.set(counter, System.currentTimeMillis() - 120_000L);

        counters.put("10.0.0.1", counter);

        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-Forwarded-For", "10.0.0.1")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, times(1)).filter(any());
        assertThat(exchange.getResponse().getStatusCode()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void filter_usesRemoteAddress_whenNoXForwardedFor() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .remoteAddress(new java.net.InetSocketAddress("10.10.10.10", 1234))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        Mono<Void> result = filter.filter(exchange, chain);

        StepVerifier.create(result)
                .verifyComplete();

        verify(chain, times(1)).filter(any());
        HttpHeaders responseHeaders = exchange.getResponse().getHeaders();
        assertThat(responseHeaders.getFirst("X-RateLimit-Limit")).isEqualTo("100");
    }

    @Test
    void filter_addsRateLimitHeaders_onAllowedRequest() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-Forwarded-For", "5.5.5.5")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        HttpHeaders headers = exchange.getResponse().getHeaders();
        assertThat(headers.getFirst("X-RateLimit-Limit")).isEqualTo("100");
        assertThat(headers.getFirst("X-RateLimit-Remaining")).isNotNull();
    }

    @Test
    void getOrder_returnsHighestPrecedencePlusOne() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
    }
}
