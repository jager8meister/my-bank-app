package ru.yandex.practicum.notifications.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.notifications.dto.NotificationEvent;

@Slf4j
@Component
public class NotificationListener {

    @KafkaListener(topics = "notifications", groupId = "notifications-group")
    public void handleNotification(NotificationEvent event) {
        if (event == null || event.login() == null || event.type() == null || event.message() == null) {
            log.warn("Received malformed notification event: {}", event);
            return;
        }
        log.info("Notification [{}] for {}: {}", event.type(), event.login(), event.message());
    }
}
