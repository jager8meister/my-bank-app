package ru.yandex.practicum.cash.dto;

public record NotificationEvent(String login, String type, String message, String timestamp) {}
