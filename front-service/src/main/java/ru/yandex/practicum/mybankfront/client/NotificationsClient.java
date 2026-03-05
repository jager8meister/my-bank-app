package ru.yandex.practicum.mybankfront.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationsClient {

    private final WebClient webClient;

    @Value("${services.notifications.url:http://notifications-service:8086}")
    private String notificationsUrl;

    public Mono<String> getPendingNotification(String login) {
        return Mono.delay(Duration.ofMillis(500))
                .then(webClient.get()
                        .uri(notificationsUrl + "/api/notifications/" + login)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                        .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(String.join(" | ", list)))
                        .timeout(Duration.ofSeconds(2))
                        .onErrorResume(e -> {
                            log.warn("Notifications service unavailable for {}: {}", login, e.getMessage());
                            return Mono.empty();
                        }));
    }

    public Mono<String> getPendingImmediate(String login) {
        return webClient.get()
                .uri(notificationsUrl + "/api/notifications/" + login)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(String.join(" | ", list)))
                .timeout(Duration.ofSeconds(1))
                .onErrorResume(e -> {
                    log.warn("Notifications service unavailable for {}: {}", login, e.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<String> saveAndGet(String login, String message) {
        return webClient.post()
                .uri(notificationsUrl + "/api/notifications/" + login)
                .bodyValue(Map.of("message", message))
                .retrieve()
                .toBodilessEntity()
                .then(webClient.get()
                        .uri(notificationsUrl + "/api/notifications/" + login)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                        .flatMap(list -> list.isEmpty() ? Mono.empty() : Mono.just(String.join(" | ", list))))
                .timeout(Duration.ofSeconds(2))
                .onErrorResume(e -> {
                    log.warn("Notifications service unavailable for {}: {}", login, e.getMessage());
                    return Mono.empty();
                });
    }
}
