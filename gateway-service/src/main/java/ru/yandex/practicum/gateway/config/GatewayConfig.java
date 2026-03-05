package ru.yandex.practicum.gateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class GatewayConfig {

    @Value("${services.auth.url:http://auth-service:8083}")
    private String authServiceUrl;

    @Value("${services.accounts.url:http://accounts-service:8081}")
    private String accountsServiceUrl;

    @Value("${services.cash.url:http://cash-service:8082}")
    private String cashServiceUrl;

    @Value("${services.transfer.url:http://transfer-service:8085}")
    private String transferServiceUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        log.info("Registering gateway routes: auth -> {}, accounts -> {}, cash -> {}, transfer -> {}",
                authServiceUrl, accountsServiceUrl, cashServiceUrl, transferServiceUrl);
        RouteLocator locator = builder.routes()
                .route("auth-token-route", r -> r
                        .path("/api/auth/token", "/api/auth/refresh")
                        .uri(authServiceUrl))
                .route("auth-service-route", r -> r
                        .path("/api/auth/**")
                        .filters(f -> f.tokenRelay())
                        .uri(authServiceUrl))
                .route("accounts-service-route", r -> r
                        .path("/api/accounts/**")
                        .filters(f -> f.tokenRelay())
                        .uri(accountsServiceUrl))
                .route("cash-service-route", r -> r
                        .path("/api/cash/**")
                        .filters(f -> f.tokenRelay())
                        .uri(cashServiceUrl))
                .route("transfer-service-route", r -> r
                        .path("/api/transfer/**")
                        .filters(f -> f.tokenRelay())
                        .uri(transferServiceUrl))
                .build();
        log.info("Gateway route registration complete: 5 routes configured");
        return locator;
    }
}
