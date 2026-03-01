package ru.yandex.practicum.transfer.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationClient {

    private final WebClient webClient;

    @Value("${services.notifications.host:notifications-service}")
    private String notificationsServiceHost;

    public Mono<Void> sendTransferNotification(String recipient, String message, String type) {
        log.info("Sending {} notification to {}", type, recipient);
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("lb")
                        .host(notificationsServiceHost)
                        .path("/api/notifications")
                        .build())
                .bodyValue(new NotificationRequest(recipient, message, type))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Notification sent successfully to {}", recipient))
                .doOnError(e -> log.error("Failed to send notification to {}: {}", recipient, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private record NotificationRequest(String recipient, String message, String type) {}
}
