package ru.yandex.practicum.transfer.dto;

public record NotificationEvent(String login, String type, String message, String timestamp) {}
