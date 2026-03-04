package ru.yandex.practicum.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.auth.dto.AuthRequest;
import ru.yandex.practicum.auth.dto.AuthResponse;
import ru.yandex.practicum.auth.dto.TokenResponse;
import ru.yandex.practicum.auth.model.User;
import ru.yandex.practicum.auth.service.AuthService;
import ru.yandex.practicum.auth.service.KeycloakAdminService;
import ru.yandex.practicum.auth.service.RegistrationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AuthController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AuthController WebFlux Tests")
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private RegistrationService registrationService;

    @MockitoBean
    private KeycloakAdminService keycloakAdminService;

    @Test
    @DisplayName("POST /api/auth/login — valid credentials → 200 with authenticated=true")
    void login_validCredentials_returns200() {
        AuthRequest request = new AuthRequest("ivanov", "password");
        AuthResponse response = new AuthResponse("ivanov", "USER", true);
        when(authService.authenticate(any(AuthRequest.class))).thenReturn(Mono.just(response));

        webTestClient
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.login").isEqualTo("ivanov")
                .jsonPath("$.role").isEqualTo("USER")
                .jsonPath("$.authenticated").isEqualTo(true);
    }

    @Test
    @DisplayName("POST /api/auth/login — wrong password → 200 with authenticated=false")
    void login_wrongPassword_returns200WithFalse() {
        AuthRequest request = new AuthRequest("ivanov", "wrongpass");
        AuthResponse response = new AuthResponse(null, null, false);
        when(authService.authenticate(any(AuthRequest.class))).thenReturn(Mono.just(response));

        webTestClient
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.authenticated").isEqualTo(false);
    }

    @Test
    @DisplayName("POST /api/auth/login — blank login → 400 Bad Request")
    void login_blankLogin_returns400() {
        String invalidBody = "{\"login\":\"\",\"password\":\"password\"}";

        webTestClient
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidBody)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/auth/login — blank password → 400 Bad Request")
    void login_blankPassword_returns400() {
        String invalidBody = "{\"login\":\"ivanov\",\"password\":\"\"}";

        webTestClient
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidBody)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/auth/login — missing login field → 400 Bad Request")
    void login_missingLoginField_returns400() {
        String invalidBody = "{\"password\":\"password\"}";

        webTestClient
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidBody)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/auth/login — missing password field → 400 Bad Request")
    void login_missingPasswordField_returns400() {
        String invalidBody = "{\"login\":\"ivanov\"}";

        webTestClient
                .post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidBody)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("GET /api/auth/validate/{login} — user exists → 200 with user data")
    void validate_userExists_returns200() {
        User user = new User(1L, "ivanov", "hashed", "USER");
        when(authService.validateUser("ivanov")).thenReturn(Mono.just(user));

        webTestClient
                .get()
                .uri("/api/auth/validate/ivanov")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.login").isEqualTo("ivanov")
                .jsonPath("$.role").isEqualTo("USER");
    }

    @Test
    @DisplayName("GET /api/auth/validate/{login} — user not found → 200 with empty body")
    void validate_userNotFound_returns200Empty() {
        when(authService.validateUser("unknown")).thenReturn(Mono.empty());

        webTestClient
                .get()
                .uri("/api/auth/validate/unknown")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .isEmpty();
    }

    @Test
    @DisplayName("POST /api/auth/token — valid credentials → 200 with token response")
    void getToken_validCredentials_returns200WithTokenResponse() {
        TokenResponse tokenResponse = new TokenResponse("access-token-xyz", "refresh-token-xyz", 300);
        when(keycloakAdminService.getUserToken(anyString(), anyString()))
                .thenReturn(Mono.just(tokenResponse));

        webTestClient
                .post()
                .uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("ivanov", "password"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.access_token").isEqualTo("access-token-xyz")
                .jsonPath("$.refresh_token").isEqualTo("refresh-token-xyz")
                .jsonPath("$.expires_in").isEqualTo(300);
    }

    @Test
    @DisplayName("POST /api/auth/token — invalid credentials → 401")
    void getToken_invalidCredentials_returns401() {
        when(keycloakAdminService.getUserToken(anyString(), anyString()))
                .thenReturn(Mono.error(WebClientResponseException.create(401, "Unauthorized", null, null, null)));

        webTestClient
                .post()
                .uri("/api/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AuthRequest("ivanov", "wrongpass"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Неверный логин или пароль");
    }

    @Test
    @DisplayName("POST /api/auth/refresh — valid token → 200 with new token response")
    void refreshToken_validToken_returns200() {
        TokenResponse tokenResponse = new TokenResponse("new-access-token", "new-refresh-token", 300);
        when(keycloakAdminService.refreshUserToken(anyString()))
                .thenReturn(Mono.just(tokenResponse));

        webTestClient
                .post()
                .uri("/api/auth/refresh?refreshToken=valid-refresh-token")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.access_token").isEqualTo("new-access-token")
                .jsonPath("$.refresh_token").isEqualTo("new-refresh-token");
    }

    @Test
    @DisplayName("POST /api/auth/refresh — invalid/expired token → 401")
    void refreshToken_invalidToken_returns401() {
        when(keycloakAdminService.refreshUserToken(anyString()))
                .thenReturn(Mono.error(new RuntimeException("Token expired")));

        webTestClient
                .post()
                .uri("/api/auth/refresh?refreshToken=expired-token")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Сессия истекла, войдите снова");
    }
}
