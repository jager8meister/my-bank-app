package ru.yandex.practicum.accounts.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationClient {

    private final WebClient webClient;

    public Mono<Void> sendAccountUpdatedNotification(String recipient, String message) {
        log.info("Sending ACCOUNT_UPDATED notification to {}", recipient);
        Map<String, Object> request = new HashMap<>();
        request.put("recipient", recipient);
        request.put("message", message);
        request.put("type", "ACCOUNT_UPDATED");
        return webClient.post()
                .uri("http://NOTIFICATIONS-SERVICE/api/notifications")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Notification sent successfully to {}", recipient))
                .doOnError(e -> log.error("Failed to send notification to {}: {}", recipient, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }
}
