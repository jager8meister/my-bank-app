package ru.yandex.practicum.transfer.client;

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

    public Mono<Void> sendTransferNotification(String recipient, String message, String type) {
        log.info("Sending {} notification to {}", type, recipient);
        Map<String, Object> request = new HashMap<>();
        request.put("recipient", recipient);
        request.put("message", message);
        request.put("type", type);
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
