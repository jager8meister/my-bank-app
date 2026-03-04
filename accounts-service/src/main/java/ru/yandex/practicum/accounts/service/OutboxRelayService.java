package ru.yandex.practicum.accounts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.client.NotificationClient;
import ru.yandex.practicum.accounts.repository.OutboxEventRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;

    private final NotificationClient notificationClient;

    @Scheduled(fixedDelay = 5000)
    public void processOutboxEvents() {
        outboxEventRepository.findByProcessedFalse()
                .concatMap(event -> notificationClient.sendNotification(
                                event.getRecipient(), event.getMessage(), event.getEventType())
                        .then(outboxEventRepository.markAsProcessed(event.getId()))
                        .doOnSuccess(v -> log.info("Outbox event {} sent to {}", event.getId(), event.getRecipient()))
                        .onErrorResume(e -> {
                            log.warn("Failed to relay outbox event {}, will retry later: {}", event.getId(), e.getMessage());
                            return Mono.empty();
                        }))
                .subscribe(
                        v -> {},
                        e -> log.error("Outbox relay pipeline terminated with unexpected error", e)
                );
    }
}
