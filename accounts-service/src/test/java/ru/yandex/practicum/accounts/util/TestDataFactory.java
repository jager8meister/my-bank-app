package ru.yandex.practicum.accounts.util;

import ru.yandex.practicum.accounts.dto.AccountDto;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.model.Account;
import java.time.LocalDate;
import java.util.List;

public class TestDataFactory {
    public static Account createTestAccount(String login, String name, LocalDate birthdate, Integer balance) {

        Account account = new Account();
        account.setLogin(login);
        account.setName(name);
        account.setBirthdate(birthdate);
        account.setBalance(balance);
        return account;
    }

    public static Account createIvanovAccount() {
        return createTestAccount("ivanov", "Иван Иванов", LocalDate.of(1990, 1, 15), 5000);
    }

    public static Account createPetrovAccount() {
        return createTestAccount("petrov", "Петр Петров", LocalDate.of(1985, 5, 20), 3000);
    }

    public static Account createSidorovAccount() {
        return createTestAccount("sidorov", "Сидор Сидоров", LocalDate.of(1995, 11, 30), 1000);
    }

    public static UpdateAccountRequest createUpdateAccountRequest(String name, LocalDate birthdate) {
        return new UpdateAccountRequest(name, birthdate);
    }

    public static AccountDto createAccountDto(String login, String name) {
        return new AccountDto(login, name);
    }

    public static List<AccountDto> createOtherAccountsList() {
        return List.of(
                createAccountDto("petrov", "Петр Петров"),
                createAccountDto("sidorov", "Сидор Сидоров")
        );
    }
}
