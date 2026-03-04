package ru.yandex.practicum.mybankfront.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mybankfront.dto.RegistrationRequest;

import java.util.Map;

@Slf4j
@Service
public class RegistrationService {

    private final WebClient webClient;

    @Value("${gateway.url:http://gateway-service:8080}")
    private String gatewayUrl;

    public RegistrationService(@Qualifier("plainWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<Void> register(RegistrationRequest request) {
        Map<String, Object> body = Map.of(
                "login", request.login(),
                "password", request.password(),
                "name", request.name(),
                "birthdate", request.birthdate().toString()
        );

        return webClient.post()
                .uri(gatewayUrl + "/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .then()
                .doOnSuccess(v -> log.info("User registered successfully: {}", request.login()))
                .doOnError(e -> log.error("Registration failed for {}: {}", request.login(), e.getMessage()));
    }
}
