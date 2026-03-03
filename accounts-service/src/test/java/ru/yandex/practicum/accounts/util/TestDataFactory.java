package ru.yandex.practicum.accounts.util;

import ru.yandex.practicum.accounts.dto.AccountDto;
import ru.yandex.practicum.accounts.model.Account;
import java.time.LocalDate;

public class TestDataFactory {
    public static Account createTestAccount(String login, String name, LocalDate birthdate, Long balance) {

        Account account = new Account();
        account.setLogin(login);
        account.setName(name);
        account.setBirthdate(birthdate);
        account.setBalance(balance);
        return account;
    }

    public static Account createIvanovAccount() {
        return createTestAccount("ivanov", "Иван Иванов", LocalDate.of(1990, 1, 15), 5000L);
    }

    public static Account createPetrovAccount() {
        return createTestAccount("petrov", "Петр Петров", LocalDate.of(1985, 5, 20), 3000L);
    }

    public static AccountDto createAccountDto(String login, String name) {
        return new AccountDto(login, name);
    }
}
