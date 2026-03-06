package ru.yandex.practicum.mybankfront.store;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class NotificationStore {

    private final Map<String, Queue<String>> store = new ConcurrentHashMap<>();

    public void save(String login, String message) {
        store.computeIfAbsent(login, k -> new ConcurrentLinkedQueue<>()).add(message);
    }

    public String pop(String login) {
        Queue<String> queue = store.get(login);
        if (queue == null) return null;
        String msg = queue.poll();
        if (queue.isEmpty()) store.remove(login);
        return msg;
    }
}
