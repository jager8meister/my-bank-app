package ru.yandex.practicum.accounts.dto;

public record NotificationEvent(String login, String type, String message, String timestamp) {}
