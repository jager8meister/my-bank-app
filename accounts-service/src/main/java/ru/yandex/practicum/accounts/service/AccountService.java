package ru.yandex.practicum.accounts.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.client.NotificationClient;
import ru.yandex.practicum.accounts.dto.AccountDto;
import ru.yandex.practicum.accounts.dto.AccountResponse;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.exception.AccountNotFoundException;
import ru.yandex.practicum.accounts.exception.InsufficientFundsException;
import ru.yandex.practicum.accounts.exception.InvalidAmountException;
import ru.yandex.practicum.accounts.exception.InvalidTransferException;
import ru.yandex.practicum.accounts.model.Account;
import ru.yandex.practicum.accounts.repository.AccountRepository;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final NotificationClient notificationClient;

    public Mono<AccountResponse> getAccountInfo(String login) {
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> getOtherAccounts(login)
                        .collectList()
                        .map(otherAccounts -> new AccountResponse(
                                account.getName(),
                                account.getBirthdate(),
                                account.getBalance(),
                                otherAccounts,
                                null,
                                null
                        ))
                );
    }

    private Flux<AccountDto> getOtherAccounts(String currentLogin) {
        return accountRepository.findAll()
                .filter(acc -> !acc.getLogin().equals(currentLogin))
                .map(acc -> new AccountDto(acc.getLogin(), acc.getName()));
    }

    @Transactional
    public Mono<AccountResponse> updateAccount(String login, UpdateAccountRequest request) {
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> {
                    account.setName(request.name());
                    account.setBirthdate(request.birthdate());
                    return accountRepository.save(account);
                })
                .flatMap(savedAccount -> {
                    return notificationClient.sendAccountUpdatedNotification(
                            savedAccount.getLogin(),
                            "Данные вашего аккаунта были обновлены: " + savedAccount.getName()
                    ).then(getAccountInfo(login));
                });
    }

    @Transactional
    public Mono<Void> updateBalance(String login, Integer newBalance) {
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> {
                    account.setBalance(newBalance);
                    return accountRepository.save(account);
                })
                .then();
    }

    public Mono<Integer> getBalance(String login) {
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .map(Account::getBalance);
    }

    @Transactional
    public Mono<Integer> depositCash(String login, Integer amount) {
        if (amount == null || amount <= 0) {
            return Mono.error(new InvalidAmountException());
        }
        // Check for integer overflow before deposit
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> {
                    // Check if adding amount would cause overflow
                    if (amount > Integer.MAX_VALUE - account.getBalance()) {
                        return Mono.error(new InvalidAmountException("Amount would cause balance overflow"));
                    }
                    // Atomic increment
                    return accountRepository.incrementBalance(login, amount)
                            .flatMap(updated -> {
                                if (updated == 0) {
                                    return Mono.error(new AccountNotFoundException(login));
                                }
                                // Fetch and return new balance
                                return getBalance(login);
                            });
                });
    }

    @Transactional
    public Mono<Integer> withdrawCash(String login, Integer amount) {
        if (amount == null || amount <= 0) {
            return Mono.error(new InvalidAmountException());
        }
        // Atomic decrement with balance check
        return accountRepository.decrementBalanceIfSufficient(login, amount)
                .flatMap(updated -> {
                    if (updated == 0) {
                        // Either account not found or insufficient funds
                        return accountRepository.findByLogin(login)
                                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                                .flatMap(account -> Mono.error(new InsufficientFundsException(account.getBalance())));
                    }
                    // Fetch and return new balance
                    return getBalance(login);
                });
    }

    @Transactional
    public Mono<TransferResult> transferMoney(String fromLogin, String toLogin, Integer amount) {
        if (amount == null || amount <= 0) {
            return Mono.error(new InvalidAmountException());
        }
        if (fromLogin.equals(toLogin)) {
            return Mono.error(new InvalidTransferException("Cannot transfer to yourself"));
        }

        // First verify both accounts exist and check for overflow
        Mono<Account> senderMono = accountRepository.findByLogin(fromLogin)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(fromLogin)));
        Mono<Account> recipientMono = accountRepository.findByLogin(toLogin)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(toLogin)));

        return Mono.zip(senderMono, recipientMono)
                .flatMap(tuple -> {
                    Account sender = tuple.getT1();
                    Account recipient = tuple.getT2();

                    // Check for overflow on recipient side
                    if (amount > Integer.MAX_VALUE - recipient.getBalance()) {
                        return Mono.error(new InvalidAmountException("Transfer would cause recipient balance overflow"));
                    }

                    // Step 1: Atomically deduct from sender (with balance check)
                    return accountRepository.decrementBalanceIfSufficient(fromLogin, amount)
                            .flatMap(senderUpdated -> {
                                if (senderUpdated == 0) {
                                    // Insufficient funds (balance check failed at DB level)
                                    return Mono.error(new InsufficientFundsException(sender.getBalance()));
                                }

                                // Step 2: Atomically add to recipient
                                return accountRepository.incrementBalance(toLogin, amount)
                                        .flatMap(recipientUpdated -> {
                                            if (recipientUpdated == 0) {
                                                // This should never happen as we verified account exists
                                                // But if it does, transaction will roll back
                                                return Mono.error(new AccountNotFoundException(toLogin));
                                            }

                                            // Step 3: Fetch updated balances and return result
                                            return Mono.zip(
                                                    accountRepository.findByLogin(fromLogin),
                                                    accountRepository.findByLogin(toLogin)
                                            ).map(updatedTuple -> new TransferResult(
                                                    updatedTuple.getT1().getBalance(),
                                                    updatedTuple.getT2().getBalance(),
                                                    updatedTuple.getT1().getName(),
                                                    updatedTuple.getT2().getName()
                                            ));
                                        });
                            });
                });
    }

    public record TransferResult(Integer senderBalance, Integer recipientBalance, String senderName, String recipientName) {}
}
