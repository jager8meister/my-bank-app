package ru.yandex.practicum.gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.test.StepVerifier;
import ru.yandex.practicum.gateway.config.GatewayConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for GatewayConfig route definitions.
 * GatewayConfig is excluded from Jacoco coverage checks (*Config.class pattern).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = GatewayConfigRouteTest.TestConfig.class)
class GatewayConfigRouteTest {

    @TestConfiguration
    @Import(GatewayConfig.class)
    static class TestConfig {

        @Bean
        public PathRoutePredicateFactory pathRoutePredicateFactory() {
            return new PathRoutePredicateFactory();
        }

        @Bean
        @SuppressWarnings("unchecked")
        public TokenRelayGatewayFilterFactory tokenRelayGatewayFilterFactory() {
            ObjectProvider<ReactiveOAuth2AuthorizedClientManager> provider =
                    mock(ObjectProvider.class);
            return new TokenRelayGatewayFilterFactory(provider);
        }

        @Bean
        public RouteLocatorBuilder routeLocatorBuilder(ConfigurableApplicationContext context) {
            return new RouteLocatorBuilder(context);
        }
    }

    @Autowired
    private RouteLocator routeLocator;

    @Test
    void customRouteLocator_createsFiveRoutes() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotNull().hasSize(5);
        assertThat(routes).extracting(Route::getId)
                .containsExactlyInAnyOrder(
                        "auth-token-route",
                        "auth-service-route",
                        "accounts-service-route",
                        "cash-service-route",
                        "transfer-service-route"
                );
    }

    @Test
    void customRouteLocator_accountsRoute_hasCorrectUri() {
        StepVerifier.create(routeLocator.getRoutes()
                        .filter(r -> r.getId().equals("accounts-service-route")))
                .assertNext(route ->
                        assertThat(route.getUri().toString())
                                .isEqualTo("http://accounts-service:8081"))
                .verifyComplete();
    }

    @Test
    void customRouteLocator_cashRoute_hasCorrectUri() {
        StepVerifier.create(routeLocator.getRoutes()
                        .filter(r -> r.getId().equals("cash-service-route")))
                .assertNext(route ->
                        assertThat(route.getUri().toString())
                                .isEqualTo("http://cash-service:8082"))
                .verifyComplete();
    }

    @Test
    void customRouteLocator_transferRoute_hasCorrectUri() {
        StepVerifier.create(routeLocator.getRoutes()
                        .filter(r -> r.getId().equals("transfer-service-route")))
                .assertNext(route ->
                        assertThat(route.getUri().toString())
                                .isEqualTo("http://transfer-service:8085"))
                .verifyComplete();
    }
}
