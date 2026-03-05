package ru.yandex.practicum.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Service
public class NotificationStore {

    private final Map<String, Queue<String>> store = new ConcurrentHashMap<>();

    public void save(String login, String message) {
        store.computeIfAbsent(login, k -> new ConcurrentLinkedQueue<>()).add(message);
        log.debug("Saved notification for login '{}': {}", login, message);
    }

    public List<String> popAll(String login) {
        Queue<String> queue = store.remove(login);
        List<String> result = queue != null ? new ArrayList<>(queue) : List.of();
        log.debug("Popped {} notification(s) for login '{}'", result.size(), login);
        return result;
    }
}
