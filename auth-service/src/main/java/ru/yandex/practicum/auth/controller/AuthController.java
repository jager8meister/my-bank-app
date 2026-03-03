package ru.yandex.practicum.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.auth.dto.AuthRequest;
import ru.yandex.practicum.auth.dto.AuthResponse;
import ru.yandex.practicum.auth.dto.RegistrationRequest;
import ru.yandex.practicum.auth.dto.TokenResponse;
import ru.yandex.practicum.auth.model.User;
import ru.yandex.practicum.auth.service.AuthService;
import ru.yandex.practicum.auth.service.KeycloakAdminService;
import ru.yandex.practicum.auth.service.RegistrationService;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Validated
@Tag(name = "Authentication", description = "User authentication and registration operations")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final KeycloakAdminService keycloakAdminService;

    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account in both the local database and Keycloak"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User registered successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid registration data"),
            @ApiResponse(responseCode = "409", description = "User with this login already exists")
    })
    @PostMapping("/register")
    public Mono<Void> register(@RequestBody @Valid RegistrationRequest request) {
        return registrationService.register(request);
    }

    @Operation(
            summary = "Authenticate a user",
            description = "Validates user credentials against the local database and returns authentication info"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authentication successful"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public Mono<AuthResponse> login(@RequestBody @Valid AuthRequest request) {
        return authService.authenticate(request);
    }

    @Operation(
            summary = "Validate user existence",
            description = "Checks whether a user with the given login exists in the system"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "User found and returned"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/validate/{login}")
    public Mono<User> validateUser(
            @Parameter(description = "The login (username) to look up")
            @PathVariable @NotBlank(message = "Login is required") String login
    ) {
        return authService.validateUser(login);
    }

    @Operation(
            summary = "Obtain a JWT token",
            description = "Authenticates the user against Keycloak using the Resource Owner Password Credentials (ROPC) flow and returns access and refresh tokens"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens returned successfully"),
            @ApiResponse(responseCode = "401", description = "Invalid login or password"),
            @ApiResponse(responseCode = "500", description = "Keycloak communication error")
    })
    @PostMapping("/token")
    public Mono<ResponseEntity<Object>> getToken(@RequestBody @Valid AuthRequest request) {
        return keycloakAdminService.getUserToken(request.login(), request.password())
                .<ResponseEntity<Object>>map(token -> ResponseEntity.ok(token))
                .onErrorResume(WebClientResponseException.class, e -> {
                    if (e.getStatusCode().value() == 401) {
                        return Mono.just(ResponseEntity.status(401).body(Map.of("error", "Неверный логин или пароль")));
                    }
                    return Mono.just(ResponseEntity.status(500).body(Map.of("error", "Ошибка авторизации")));
                })
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500).body(Map.of("error", "Ошибка авторизации"))));
    }

    @Operation(
            summary = "Refresh a JWT token",
            description = "Uses a Keycloak refresh token to issue a new access token without requiring user credentials"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New tokens returned successfully"),
            @ApiResponse(responseCode = "401", description = "Refresh token is expired or invalid")
    })
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Object>> refreshToken(
            @Parameter(description = "The refresh token previously issued by Keycloak")
            @RequestParam String refreshToken
    ) {
        return keycloakAdminService.refreshUserToken(refreshToken)
                .<ResponseEntity<Object>>map(token -> ResponseEntity.ok(token))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(401).body(Map.of("error", "Сессия истекла, войдите снова"))));
    }
}
