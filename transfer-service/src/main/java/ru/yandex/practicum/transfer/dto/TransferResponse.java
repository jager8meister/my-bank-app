package ru.yandex.practicum.transfer.dto;

public record TransferResponse(
        boolean success,
        String message,
        Long senderBalance,
        Long recipientBalance
) {
}
