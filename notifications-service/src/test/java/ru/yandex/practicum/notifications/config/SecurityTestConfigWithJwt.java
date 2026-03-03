package ru.yandex.practicum.notifications.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Test security configuration that mirrors the real SecurityConfig but supplies
 * a no-op ReactiveJwtDecoder so the ApplicationContext can start without a
 * real Keycloak issuer-uri.
 *
 * The exchange-level rule also enforces SCOPE_microservice-scope on /api/notifications
 * so that AccessDeniedException is raised in the filter chain (resulting in 403)
 * rather than inside the controller (where GlobalExceptionHandler would catch it).
 */
@TestConfiguration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityTestConfigWithJwt {

    @Bean
    public ReactiveJwtDecoder testJwtDecoder() {
        return token -> Mono.empty();
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**").permitAll()
                        // enforce scope at the filter-chain level so that 403 is returned
                        // correctly even when GlobalExceptionHandler has a catch-all handler
                        .pathMatchers("/api/notifications").hasAuthority("SCOPE_microservice-scope")
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(testJwtDecoder()))
                )
                .csrf(ServerHttpSecurity.CsrfSpec::disable);
        return http.build();
    }
}
