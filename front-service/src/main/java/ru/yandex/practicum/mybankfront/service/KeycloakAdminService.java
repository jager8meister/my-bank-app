package ru.yandex.practicum.mybankfront.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class KeycloakAdminService {

    private final WebClient webClient;

    @Value("${keycloak.admin.url:http://keycloak:9090}")
    private String adminUrl;

    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:admin}")
    private String adminPassword;

    @Value("${keycloak.admin.realm:bank-realm}")
    private String realm;

    public KeycloakAdminService(@Qualifier("plainWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> getAdminToken() {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", "admin-cli");
        formData.add("username", adminUsername);
        formData.add("password", adminPassword);

        return webClient.post()
                .uri(adminUrl + "/realms/master/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("access_token"))
                .doOnError(e -> log.error("Failed to get admin token: {}", e.getMessage()));
    }

    public Mono<Boolean> userExists(String adminToken, String login) {
        return webClient.get()
                .uri(adminUrl + "/admin/realms/" + realm + "/users?username={login}&exact=true", login)
                .headers(h -> h.setBearerAuth(adminToken))
                .retrieve()
                .bodyToFlux(Map.class)
                .hasElements();
    }

    public Mono<String> createUser(String adminToken, String login) {
        Map<String, Object> userRep = Map.of(
                "username", login,
                "enabled", true,
                "emailVerified", true
        );

        return webClient.post()
                .uri(adminUrl + "/admin/realms/" + realm + "/users")
                .headers(h -> h.setBearerAuth(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(userRep)
                .retrieve()
                .toBodilessEntity()
                .map(response -> {
                    String location = response.getHeaders().getFirst("Location");
                    if (location == null) {
                        throw new RuntimeException("No Location header in create user response");
                    }
                    return location.substring(location.lastIndexOf('/') + 1);
                })
                .doOnSuccess(id -> log.info("Created Keycloak user: {} with id: {}", login, id))
                .doOnError(e -> log.error("Failed to create Keycloak user {}: {}", login, e.getMessage()));
    }

    public Mono<Void> setPassword(String adminToken, String userId, String password) {
        Map<String, Object> credentialRep = Map.of(
                "type", "password",
                "value", password,
                "temporary", false
        );

        return webClient.put()
                .uri(adminUrl + "/admin/realms/" + realm + "/users/{userId}/reset-password", userId)
                .headers(h -> h.setBearerAuth(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(credentialRep)
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnError(e -> log.error("Failed to set password for user {}: {}", userId, e.getMessage()));
    }

    public Mono<List<Map>> getRealmRolesByName(String adminToken, List<String> roleNames) {
        return Flux.fromIterable(roleNames)
                .flatMap(roleName -> webClient.get()
                        .uri(adminUrl + "/admin/realms/" + realm + "/roles/{roleName}", roleName)
                        .headers(h -> h.setBearerAuth(adminToken))
                        .retrieve()
                        .bodyToMono(Map.class))
                .collectList();
    }

    public Mono<Void> assignRoles(String adminToken, String userId, List<Map> roles) {
        return webClient.post()
                .uri(adminUrl + "/admin/realms/" + realm + "/users/{userId}/role-mappings/realm", userId)
                .headers(h -> h.setBearerAuth(adminToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(roles)
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnSuccess(v -> log.info("Assigned {} roles to user {}", roles.size(), userId))
                .doOnError(e -> log.error("Failed to assign roles to user {}: {}", userId, e.getMessage()));
    }

    public Mono<Void> deleteUser(String adminToken, String userId) {
        return webClient.delete()
                .uri(adminUrl + "/admin/realms/" + realm + "/users/{userId}", userId)
                .headers(h -> h.setBearerAuth(adminToken))
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnSuccess(v -> log.info("Rolled back: deleted Keycloak user {}", userId))
                .doOnError(e -> log.error("Failed to delete Keycloak user {} during rollback: {}", userId, e.getMessage()));
    }
}
