package ru.yandex.practicum.mybankfront.dto;

public record NotificationEvent(String login, String type, String message, String timestamp) {}
