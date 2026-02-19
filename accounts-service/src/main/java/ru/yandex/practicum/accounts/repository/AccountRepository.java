package ru.yandex.practicum.accounts.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.model.Account;

@Repository
public interface AccountRepository extends R2dbcRepository<Account, Long> {
    Mono<Account> findByLogin(String login);

    /**
     * Atomically increment balance (for deposits)
     * @return number of updated rows (1 if successful, 0 if account not found)
     */
    @Modifying
    @Query("UPDATE accounts_schema.accounts SET balance = balance + :amount, version = version + 1 WHERE login = :login")
    Mono<Integer> incrementBalance(String login, Integer amount);

    /**
     * Atomically decrement balance if sufficient funds exist (for withdrawals and transfers)
     * @return number of updated rows (1 if successful, 0 if insufficient funds or account not found)
     */
    @Modifying
    @Query("UPDATE accounts_schema.accounts SET balance = balance - :amount, version = version + 1 WHERE login = :login AND balance >= :amount")
    Mono<Integer> decrementBalanceIfSufficient(String login, Integer amount);
}
