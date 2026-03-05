package ru.yandex.practicum.accounts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.dto.NotificationEvent;
import ru.yandex.practicum.accounts.repository.OutboxEventRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Scheduled(fixedDelay = 5000)
    public void processOutboxEvents() {
        outboxEventRepository.findByProcessedFalse()
                .concatMap(event -> {
                    NotificationEvent notificationEvent = new NotificationEvent(
                            event.getRecipient(),
                            event.getEventType(),
                            event.getMessage(),
                            LocalDateTime.now().toString()
                    );
                    return Mono.fromFuture(
                                    kafkaTemplate.send("notifications", event.getRecipient(), notificationEvent))
                            .then(outboxEventRepository.markAsProcessed(event.getId()))
                            .doOnSuccess(v -> log.info("Outbox event {} sent to {}", event.getId(), event.getRecipient()))
                            .onErrorResume(e -> {
                                log.warn("Failed to relay outbox event {}, will retry later: {}", event.getId(), e.getMessage());
                                return Mono.empty();
                            });
                })
                .subscribe(
                        v -> {},
                        e -> log.error("Outbox relay pipeline terminated with unexpected error", e)
                );
    }
}
