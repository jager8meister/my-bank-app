package ru.yandex.practicum.mybankfront.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.mybankfront.dto.NotificationEvent;
import ru.yandex.practicum.mybankfront.store.NotificationStore;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationStore notificationStore;

    @KafkaListener(topics = "notifications", groupId = "front-notifications-group")
    public void handleNotification(NotificationEvent event) {
        if (event == null || event.login() == null) return;
        log.info("Notification [{}] for {}: {}", event.type(), event.login(), event.message());
        notificationStore.save(event.login(), event.message());
    }
}
