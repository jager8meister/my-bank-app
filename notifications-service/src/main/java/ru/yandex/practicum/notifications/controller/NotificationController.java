package ru.yandex.practicum.notifications.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.yandex.practicum.notifications.service.NotificationStore;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationStore store;

    @GetMapping("/{login}")
    public List<String> getNotifications(@PathVariable String login) {
        List<String> notifications = store.popAll(login);
        log.info("GET notifications for login '{}': returning {} notification(s)", login, notifications.size());
        return notifications;
    }

    @PostMapping("/{login}")
    public void saveNotification(@PathVariable String login, @RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message != null && !message.isBlank()) {
            store.save(login, message);
            log.info("POST notification saved for login '{}'", login);
        } else {
            log.warn("POST notification for login '{}' ignored: message is blank or missing", login);
        }
    }
}
