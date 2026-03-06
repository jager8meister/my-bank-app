package ru.yandex.practicum.notifications.dto;

public record NotificationEvent(String login, String type, String message, String timestamp) {}
