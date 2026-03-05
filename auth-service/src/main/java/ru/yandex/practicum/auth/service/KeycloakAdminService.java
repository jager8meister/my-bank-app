package ru.yandex.practicum.auth.service;

import lombok.extern.slf4j.Slf4j;
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
import ru.yandex.practicum.auth.dto.TokenResponse;

@Slf4j
@Service
public class KeycloakAdminService {

    private final WebClient webClient;

    @Value("${keycloak.admin.url}")
    private String adminUrl;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.client.id}")
    private String clientId;

    @Value("${keycloak.client.secret}")
    private String clientSecret;

    public KeycloakAdminService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<TokenResponse> getUserToken(String username, String password) {
        log.info("Requesting Keycloak token for user: {}", username);
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("username", username);
        formData.add("password", password);

        return webClient.post()
                .uri(adminUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .doOnSuccess(t -> log.info("Keycloak token obtained for user: {}", username))
                .doOnError(e -> log.error("Failed to get user token for {}: {}", username, e.getMessage()));
    }

    public Mono<TokenResponse> refreshUserToken(String refreshToken) {
        log.info("Requesting Keycloak token refresh");
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("refresh_token", refreshToken);

        return webClient.post()
                .uri(adminUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .doOnSuccess(t -> log.info("Keycloak token refresh succeeded"))
                .doOnError(e -> log.error("Failed to refresh token: {}", e.getMessage()));
    }

    public Mono<String> getAdminToken() {
        log.info("Requesting Keycloak admin token for realm: master");
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
                .doOnSuccess(t -> log.info("Keycloak admin token obtained successfully"))
                .doOnError(e -> log.error("Failed to get admin token: {}", e.getMessage()));
    }

    public Mono<Boolean> userExists(String adminToken, String login) {
        log.info("Checking Keycloak user existence for login: {}", login);
        return webClient.get()
                .uri(adminUrl + "/admin/realms/" + realm + "/users?username={login}&exact=true", login)
                .headers(h -> h.setBearerAuth(adminToken))
                .retrieve()
                .bodyToFlux(Map.class)
                .hasElements()
                .doOnNext(exists -> log.debug("User existence check for {}: exists={}", login, exists));
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
        log.info("Fetching {} realm roles from Keycloak: {}", roleNames.size(), roleNames);
        return Flux.fromIterable(roleNames)
                .flatMap(roleName -> webClient.get()
                        .uri(adminUrl + "/admin/realms/" + realm + "/roles/{roleName}", roleName)
                        .headers(h -> h.setBearerAuth(adminToken))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .doOnError(e -> log.warn("Failed to fetch realm role '{}': {}", roleName, e.getMessage())))
                .collectList()
                .doOnSuccess(roles -> log.info("Successfully fetched {} realm roles", roles.size()));
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
