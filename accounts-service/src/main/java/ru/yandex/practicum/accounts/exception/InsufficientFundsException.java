package ru.yandex.practicum.accounts.exception;

public class InsufficientFundsException extends RuntimeException {

    private final Long availableBalance;

    public InsufficientFundsException(Long availableBalance) {
        super("Insufficient funds. Available: " + availableBalance);
        this.availableBalance = availableBalance;
    }

    public Long getAvailableBalance() {
        return availableBalance;
    }
}
