package ru.yandex.practicum.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);
    private static final int MAX_REQUESTS_PER_MINUTE = 100;

    private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);

    private final Map<String, RequestCounter> requestCounters = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = getClientIp(exchange);
        RequestCounter counter = requestCounters.computeIfAbsent(
                clientIp,
                k -> new RequestCounter()
        );
        if (counter.incrementAndCheck() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Rate limit exceeded for IP: {}", clientIp);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
            exchange.getResponse().getHeaders().add("X-RateLimit-Remaining", "0");
            return exchange.getResponse().setComplete();
        }
        exchange.getResponse().getHeaders().add("X-RateLimit-Limit", String.valueOf(MAX_REQUESTS_PER_MINUTE));
        exchange.getResponse().getHeaders().add("X-RateLimit-Remaining",
                String.valueOf(MAX_REQUESTS_PER_MINUTE - counter.getCount()));
        return chain.filter(exchange);
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
    private static class RequestCounter {

        private final AtomicInteger count = new AtomicInteger(0);

        private volatile long windowStart = System.currentTimeMillis();

        public int incrementAndCheck() {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_DURATION.toMillis()) {
                synchronized (this) {
                    if (now - windowStart > WINDOW_DURATION.toMillis()) {
                        count.set(1);
                        windowStart = now;
                        return 1;
                    }
                }
            }
            return count.incrementAndGet();
        }

        public int getCount() {
            return count.get();
        }
    }
}
