package ru.yandex.practicum.notifications.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.notifications.dto.NotificationEvent;
import ru.yandex.practicum.notifications.service.NotificationStore;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationStore store;

    @KafkaListener(topics = "notifications", groupId = "notifications-group")
    public void handleNotification(NotificationEvent event) {
        if (event == null || event.login() == null || event.type() == null || event.message() == null) {
            log.warn("Received malformed notification event: {}", event);
            return;
        }
        log.info("Received notification event [{}] for login '{}': {}", event.type(), event.login(), event.message());
        store.save(event.login(), event.message());
    }
}
