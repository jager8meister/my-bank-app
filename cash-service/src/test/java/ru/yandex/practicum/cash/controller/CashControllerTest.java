package ru.yandex.practicum.cash.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.cash.config.TestSecurityConfig;
import ru.yandex.practicum.cash.dto.CashAction;
import ru.yandex.practicum.cash.dto.CashOperationRequest;
import ru.yandex.practicum.cash.dto.CashResponse;
import ru.yandex.practicum.cash.exception.CashOperationException;
import ru.yandex.practicum.cash.exception.ForbiddenException;
import ru.yandex.practicum.cash.service.CashService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(
        controllers = CashController.class,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.cloud.discovery.enabled=false"
        },
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration.class
        }
)
@Import(TestSecurityConfig.class)
@DisplayName("CashController WebFlux Tests")
class CashControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CashService cashService;

    @Test
    @DisplayName("POST /api/cash/{login} with valid request and user JWT → 200")
    void shouldReturnOkForValidRequestWithUserJwt() {
        CashOperationRequest request = new CashOperationRequest(500L, CashAction.PUT);
        CashResponse response = new CashResponse(5500L, null, "Положено 500 руб");

        when(cashService.processCashOperation(eq("ivanov"), any(CashOperationRequest.class)))
                .thenReturn(Mono.just(response));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "ivanov"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.newBalance").isEqualTo(5500)
                .jsonPath("$.info").isEqualTo("Положено 500 руб");
    }

    @Test
    @DisplayName("POST /api/cash/{login} for withdrawal (GET action) → 200")
    void shouldReturnOkForWithdrawal() {
        CashOperationRequest request = new CashOperationRequest(500L, CashAction.GET);
        CashResponse response = new CashResponse(4500L, null, "Снято 500 руб");

        when(cashService.processCashOperation(eq("ivanov"), any(CashOperationRequest.class)))
                .thenReturn(Mono.just(response));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "ivanov"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.newBalance").isEqualTo(4500)
                .jsonPath("$.info").isEqualTo("Снято 500 руб");
    }

    @Test
    @DisplayName("POST /api/cash/{login} without authorization → 401")
    void shouldReturn401WhenNoAuthorization() {
        CashOperationRequest request = new CashOperationRequest(500L, CashAction.PUT);

        when(cashService.processCashOperation(any(), any()))
                .thenReturn(Mono.empty());

        webTestClient
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("POST /api/cash/{login} with null value → 400")
    void shouldReturn400ForNullValue() {
        String invalidBody = "{\"value\":null,\"action\":\"PUT\"}";

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "ivanov"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidBody)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/cash/{login} with zero value → 400")
    void shouldReturn400ForZeroValue() {
        String invalidBody = "{\"value\":0,\"action\":\"PUT\"}";

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "ivanov"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidBody)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/cash/{login} with missing action → 400")
    void shouldReturn400ForMissingAction() {
        String invalidBody = "{\"value\":500}";

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "ivanov"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidBody)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/cash/{login} accessing another user's account → 403")
    void shouldReturn403WhenAccessingAnotherUsersAccount() {
        CashOperationRequest request = new CashOperationRequest(500L, CashAction.PUT);

        when(cashService.processCashOperation(any(), any()))
                .thenReturn(Mono.error(new ForbiddenException("You can only perform operations on your own account")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "petrov"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("POST /api/cash/{login} when service throws CashOperationException → 400")
    void shouldReturn400WhenCashOperationExceptionThrown() {
        CashOperationRequest request = new CashOperationRequest(500L, CashAction.PUT);

        when(cashService.processCashOperation(eq("ivanov"), any(CashOperationRequest.class)))
                .thenReturn(Mono.error(new CashOperationException("Сервис временно недоступен. Попробуйте позже.")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "ivanov"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Сервис временно недоступен. Попробуйте позже.");
    }

    @Test
    @DisplayName("POST /api/cash/{login} with SCOPE_microservice-scope bypasses owner check → 200")
    void shouldAllowMicroserviceScopeToAccessAnyAccount() {
        CashOperationRequest request = new CashOperationRequest(1000L, CashAction.PUT);
        CashResponse response = new CashResponse(11000L, null, "Положено 1000 руб");

        when(cashService.processCashOperation(eq("ivanov"), any(CashOperationRequest.class)))
                .thenReturn(Mono.just(response));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "other-service").claim("sub", "other-service"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.newBalance").isEqualTo(11000);
    }
}
