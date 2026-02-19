package ru.yandex.practicum.transfer.dto;

public record TransferResponse(
        boolean success,
        String message,
        Integer senderBalance,
        Integer recipientBalance
) {
}
