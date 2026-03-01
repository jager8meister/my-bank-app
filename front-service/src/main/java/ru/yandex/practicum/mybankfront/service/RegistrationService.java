package ru.yandex.practicum.mybankfront.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mybankfront.dto.RegistrationRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RegistrationService {

    private static final List<String> DEFAULT_ROLES =
            List.of("USER", "Accounts", "Cash", "Transfer", "Notifications");

    private final KeycloakAdminService keycloakAdminService;
    private final WebClient webClient;

    @Value("${gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    public RegistrationService(KeycloakAdminService keycloakAdminService,
                               @Qualifier("plainWebClient") WebClient webClient) {
        this.keycloakAdminService = keycloakAdminService;
        this.webClient = webClient;
    }

    public Mono<Void> register(RegistrationRequest request) {
        return keycloakAdminService.getAdminToken()
                .flatMap(adminToken -> keycloakAdminService.userExists(adminToken, request.login())
                        .flatMap(exists -> {
                            if (exists) {
                                return Mono.error(new RuntimeException("Логин уже занят"));
                            }
                            return keycloakAdminService.createUser(adminToken, request.login())
                                    .flatMap(userId ->
                                            keycloakAdminService.setPassword(adminToken, userId, request.password())
                                                    .then(keycloakAdminService.getRealmRolesByName(adminToken, DEFAULT_ROLES))
                                                    .flatMap(roles -> keycloakAdminService.assignRoles(adminToken, userId, roles))
                                                    .then(createAccountsRecord(request))
                                                    .onErrorResume(e ->
                                                            keycloakAdminService.deleteUser(adminToken, userId)
                                                                    .then(Mono.error(e))
                                                    )
                                    );
                        })
                );
    }

    private Mono<Void> createAccountsRecord(RegistrationRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("login", request.login());
        body.put("name", request.name());
        body.put("birthdate", request.birthdate().toString());

        return webClient.post()
                .uri(gatewayUrl + "/api/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnSuccess(v -> log.info("Created accounts record for user: {}", request.login()))
                .doOnError(e -> log.error("Failed to create accounts record for {}: {}", request.login(), e.getMessage()));
    }
}
