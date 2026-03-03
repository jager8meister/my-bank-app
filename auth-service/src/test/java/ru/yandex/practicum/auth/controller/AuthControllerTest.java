package ru.yandex.practicum.auth.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.auth.dto.AuthRequest;
import ru.yandex.practicum.auth.dto.AuthResponse;
import ru.yandex.practicum.auth.model.User;
import ru.yandex.practicum.auth.service.AuthService;
import ru.yandex.practicum.auth.service.KeycloakAdminService;
import ru.yandex.practicum.auth.service.RegistrationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AuthController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AuthController WebFlux Tests")
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AuthService authService;

    @MockBean
    private RegistrationService registrationService;

    @MockBean
    private KeycloakAdminService keycloakAdminService;

    // -------------------------------------------------------------------------
    // POST /api/auth/login
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // GET /api/auth/validate/{login}
    // -------------------------------------------------------------------------

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
}
