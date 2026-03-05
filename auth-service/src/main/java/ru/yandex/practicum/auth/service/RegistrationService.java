package ru.yandex.practicum.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.auth.dto.RegistrationRequest;
import ru.yandex.practicum.auth.exception.UserAlreadyExistsException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationService {

    private static final List<String> DEFAULT_ROLES =
            List.of("USER", "Accounts", "Cash", "Transfer", "Notifications");

    private final KeycloakAdminService keycloakAdminService;

    private final WebClient webClient;

    @Value("${services.accounts.url:http://accounts-service:8081}")
    private String accountsServiceUrl;

    public Mono<Void> register(RegistrationRequest request) {
        log.info("Starting registration for user: {}", request.login());
        return keycloakAdminService.getAdminToken()
                .flatMap(adminToken -> keycloakAdminService.userExists(adminToken, request.login())
                        .flatMap(exists -> {
                            if (exists) {
                                log.warn("Registration rejected - user already exists: {}", request.login());
                                return Mono.error(new UserAlreadyExistsException(request.login()));
                            }
                            log.info("User {} not found in Keycloak - proceeding with creation", request.login());
                            return keycloakAdminService.createUser(adminToken, request.login())
                                    .flatMap(userId ->
                                            keycloakAdminService.setPassword(adminToken, userId, request.password())
                                                    .then(keycloakAdminService.getRealmRolesByName(adminToken, DEFAULT_ROLES))
                                                    .flatMap(roles -> keycloakAdminService.assignRoles(adminToken, userId, roles))
                                                    .then(createAccountsRecord(request))
                                                    .doOnSuccess(v -> log.info("Registration completed successfully for user: {}", request.login()))
                                                    .onErrorResume(e -> {
                                                        log.error("Registration failed for user: {} - initiating Keycloak rollback: {}", request.login(), e.getMessage());
                                                        return keycloakAdminService.deleteUser(adminToken, userId)
                                                                .onErrorResume(rollbackError -> {
                                                                    log.error("Rollback failed: could not delete Keycloak user {} after registration error: {}",
                                                                            userId, rollbackError.getMessage());
                                                                    return Mono.empty();
                                                                })
                                                                .then(Mono.error(e));
                                                    })
                                    );
                        })
                );
    }

    private Mono<Void> createAccountsRecord(RegistrationRequest request) {
        Map<String, Object> body = Map.of(
                "login", request.login(),
                "name", request.name(),
                "birthdate", request.birthdate().toString()
        );

        return webClient.post()
                .uri(accountsServiceUrl + "/api/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnSuccess(v -> log.info("Created accounts record for user: {}", request.login()))
                .doOnError(e -> log.error("Failed to create accounts record for {}: {}", request.login(), e.getMessage()));
    }
}
