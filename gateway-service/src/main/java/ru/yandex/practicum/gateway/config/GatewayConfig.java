package ru.yandex.practicum.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("accounts-service-route", r -> r
                        .path("/api/accounts/**")
                        .filters(f -> f.tokenRelay())
                        .uri("lb://ACCOUNTS-SERVICE"))
                .route("cash-service-route", r -> r
                        .path("/api/cash/**")
                        .filters(f -> f.tokenRelay())
                        .uri("lb://CASH-SERVICE"))
                .route("transfer-service-route", r -> r
                        .path("/api/transfer/**")
                        .filters(f -> f.tokenRelay())
                        .uri("lb://TRANSFER-SERVICE"))
                .route("front-service-route", r -> r
                        .path("/**")
                        .uri("lb://FRONT-SERVICE"))
                .build();
    }
}
