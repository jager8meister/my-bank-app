package ru.yandex.practicum.transfer.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.transfer.config.TestSecurityConfig;
import ru.yandex.practicum.transfer.dto.TransferRequest;
import ru.yandex.practicum.transfer.dto.TransferResponse;
import ru.yandex.practicum.transfer.service.TransferService;
import java.time.Instant;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
@WebFluxTest(
        controllers = TransferController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration.class,
                org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration.class
        }
)
@Import(TestSecurityConfig.class)
class TransferControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TransferService transferService;
    @Test
    void shouldTransferMoneyForAuthorizedUser() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 500L);
        TransferResponse response = new TransferResponse(true, "Transfer successful", 4500L, 5500L);
        when(transferService.transfer(any(TransferRequest.class))).thenReturn(Mono.just(response));
        Authentication auth = createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockAuthentication(auth))
                .post()
                .uri("/api/transfer")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.message").isEqualTo("Transfer successful")
                .jsonPath("$.senderBalance").isEqualTo(4500)
                .jsonPath("$.recipientBalance").isEqualTo(5500);
    }
    @Test
    void shouldRejectTransferFromDifferentAccount() {
        TransferRequest request = new TransferRequest("petrov", "sidorov", 500L);
        when(transferService.transfer(any(TransferRequest.class))).thenReturn(Mono.empty());
        Authentication auth = createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockAuthentication(auth))
                .post()
                .uri("/api/transfer")
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
    }
    @Test
    void shouldAllowServiceAccountToTransferFromAnyAccount() {
        TransferRequest request = new TransferRequest("petrov", "sidorov", 500L);
        TransferResponse response = new TransferResponse(true, "Transfer successful", 4500L, 5500L);
        when(transferService.transfer(any(TransferRequest.class))).thenReturn(Mono.just(response));
        Authentication auth = createServiceAuthentication();
        webTestClient
                .mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockAuthentication(auth))
                .post()
                .uri("/api/transfer")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }
    @Test
    void shouldHandleTransferFailure() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", 10000L);
        TransferResponse response = new TransferResponse(false, "Insufficient funds", null, null);
        when(transferService.transfer(any(TransferRequest.class))).thenReturn(Mono.just(response));
        Authentication auth = createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockAuthentication(auth))
                .post()
                .uri("/api/transfer")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.message").isEqualTo("Insufficient funds");
    }
    @Test
    void shouldHandleInvalidRequest() {
        TransferRequest request = new TransferRequest("ivanov", "petrov", -100L);
        Authentication auth = createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers
                        .mockAuthentication(auth))
                .post()
                .uri("/api/transfer")
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    private Authentication createUserAuthentication(String username) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", username)
                .claim("preferred_username", username)
                .claim("scope", "openid profile")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private Authentication createServiceAuthentication() {
        Jwt jwt = Jwt.withTokenValue("test-service-token")
                .header("alg", "RS256")
                .claim("sub", "microservices-client")
                .claim("scope", "microservice-scope")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_microservice-scope")));
    }
}
