package ru.yandex.practicum.notifications.listener;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.notifications.dto.NotificationEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final MeterRegistry meterRegistry;

    @KafkaListener(topics = "notifications", groupId = "notifications-group")
    public void handleNotification(NotificationEvent event) {
        if (event == null || event.login() == null || event.type() == null || event.message() == null) {
            log.warn("Received malformed notification event: {}", event);
            String login = (event != null && event.login() != null) ? event.login() : "unknown";
            meterRegistry.counter("notification.send.failures", "login", login).increment();
            return;
        }
        try {
            log.debug("Processing notification event: type={}, login={}, timestamp={}", event.type(), event.login(), event.timestamp());
            log.info("Notification [{}] for {}: {}", event.type(), event.login(), event.message());
        } catch (Exception e) {
            log.error("Failed to process notification for {}: {}", event.login(), e.getMessage(), e);
            meterRegistry.counter("notification.send.failures", "login", event.login()).increment();
        }
    }
}
