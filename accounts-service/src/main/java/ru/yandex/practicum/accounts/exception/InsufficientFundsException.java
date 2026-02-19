package ru.yandex.practicum.accounts.exception;

public class InsufficientFundsException extends RuntimeException {

    private final Integer availableBalance;

    public InsufficientFundsException(Integer availableBalance) {
        super("Insufficient funds. Available: " + availableBalance);
        this.availableBalance = availableBalance;
    }

    public Integer getAvailableBalance() {
        return availableBalance;
    }
}
