package ru.yandex.practicum.transfer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.transfer.config.SecurityConfig;
import ru.yandex.practicum.transfer.dto.TransferRequest;
import ru.yandex.practicum.transfer.dto.TransferResponse;
import ru.yandex.practicum.transfer.exception.InsufficientFundsException;
import ru.yandex.practicum.transfer.service.TransferService;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Controller tests that load the REAL SecurityConfig to verify HTTP-level
 * security behaviour (401, 403).  A ReactiveJwtDecoder is mocked so that
 * no Keycloak server is needed.
 */
@WebFluxTest(
        controllers = TransferController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration.class
        }
)
@Import(SecurityConfig.class)
@DisplayName("TransferController security integration tests")
class TransferControllerSecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private TransferService transferService;

    /**
     * The resource server auto-configuration tries to contact an issuer URI.
     * Mocking ReactiveJwtDecoder prevents that network call.
     */
    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Test
    @DisplayName("POST /api/transfer without auth → 401")
    void shouldReturn401_whenNoAuthentication() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);

        webTestClient
                .post()
                .uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("POST /api/transfer with valid JWT → 200")
    void shouldReturn200_whenAuthenticatedOwner() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);
        TransferResponse response = new TransferResponse(true, "Transfer successful", 4500L, 5500L);

        when(transferService.transfer(any(TransferRequest.class))).thenReturn(Mono.just(response));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "ivanov"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .post()
                .uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Transfer successful");
    }

    @Test
    @DisplayName("POST /api/transfer with negative amount → 400")
    void shouldReturn400_whenAmountIsNegative() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", -100L);

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "ivanov"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .post()
                .uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/transfer with null senderLogin → 400")
    void shouldReturn400_whenSenderLoginIsBlank() {
        String json = "{\"senderLogin\":\"\",\"recipientLogin\":\"petrov\",\"amount\":100}";

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "ivanov"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .post()
                .uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(json)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/transfer from another user's account → 403")
    void shouldReturn403_whenTransferringFromSomeoneElsesAccount() {
        TransferRequest request = new TransferRequest("petrov", "sidorov", 500L);
        when(transferService.transfer(any(TransferRequest.class))).thenReturn(Mono.empty());

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "ivanov"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .post()
                .uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("POST /api/transfer with SCOPE_microservice-scope → 200 (bypass ownership)")
    void shouldReturn200_whenServiceAccountTransfersFromAnyAccount() {
        TransferRequest request = new TransferRequest("petrov", "sidorov", 500L);
        TransferResponse response = new TransferResponse(true, "Transfer successful", 4500L, 5500L);

        when(transferService.transfer(any(TransferRequest.class))).thenReturn(Mono.just(response));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "microservices-client"))
                        .authorities(new SimpleGrantedAuthority("SCOPE_microservice-scope")))
                .post()
                .uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    @Test
    @DisplayName("POST /api/transfer when service throws InsufficientFundsException → 400")
    void shouldReturn400_whenInsufficientFunds() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 999999L);

        when(transferService.transfer(any(TransferRequest.class)))
                .thenReturn(Mono.error(new InsufficientFundsException("Insufficient funds")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockJwt()
                        .jwt(j -> j.claim("preferred_username", "ivanov"))
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .post()
                .uri("/api/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Insufficient funds");
    }
}
