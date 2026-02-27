package ru.yandex.practicum.accounts.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.dto.AccountDto;
import ru.yandex.practicum.accounts.model.Account;

@Repository
public interface AccountRepository extends R2dbcRepository<Account, Long> {

    Mono<Account> findByLogin(String login);

    /**
     * Returns all accounts except the one with the given login.
     */
    @Query("SELECT login, name FROM accounts_schema.accounts WHERE login != :login")
    Flux<AccountDto> findOtherAccounts(String login);

    /**
     * Atomically increment balance (for deposits)
     * @return number of updated rows (1 if successful, 0 if account not found)
     */
    @Modifying
    @Query("UPDATE accounts_schema.accounts SET balance = balance + :amount, version = version + 1 WHERE login = :login")
    Mono<Integer> incrementBalance(String login, Long amount);

    /**
     * Atomically decrement balance if sufficient funds exist (for withdrawals and transfers)
     * @return number of updated rows (1 if successful, 0 if insufficient funds or account not found)
     */
    @Modifying
    @Query("UPDATE accounts_schema.accounts SET balance = balance - :amount, version = version + 1 WHERE login = :login AND balance >= :amount")
    Mono<Integer> decrementBalanceIfSufficient(String login, Long amount);

    /**
     * Insert a new account with zero balance (bypasses optimistic locking for new entity creation)
     */
    @Modifying
    @Query("INSERT INTO accounts_schema.accounts (version, login, name, birthdate, balance) VALUES (0, :login, :name, :birthdate, 0)")
    Mono<Integer> insertAccount(String login, String name, java.time.LocalDate birthdate);
}
