package ru.yandex.practicum.accounts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.dto.AccountResponse;
import ru.yandex.practicum.accounts.dto.CreateAccountRequest;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.exception.AccountAlreadyExistsException;
import ru.yandex.practicum.accounts.exception.AccountNotFoundException;
import ru.yandex.practicum.accounts.exception.InsufficientFundsException;
import ru.yandex.practicum.accounts.exception.InvalidAmountException;
import ru.yandex.practicum.accounts.exception.InvalidTransferException;
import ru.yandex.practicum.accounts.model.Account;
import ru.yandex.practicum.accounts.model.OutboxEvent;
import ru.yandex.practicum.accounts.repository.AccountRepository;
import ru.yandex.practicum.accounts.repository.OutboxEventRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(readOnly = true)
    public Mono<AccountResponse> getAccountInfo(String login) {
        log.info("Fetching account info for login='{}'", login);
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> accountRepository.findOtherAccounts(login).collectList()
                        .map(otherAccounts -> {
                            log.debug("Account info loaded for login='{}', balance={}, otherAccounts={}",
                                    login, account.getBalance(), otherAccounts.size());
                            return new AccountResponse(
                                    account.getName(),
                                    account.getBirthdate(),
                                    account.getBalance(),
                                    otherAccounts,
                                    null,
                                    null
                            );
                        }));
    }

    @Transactional
    public Mono<AccountResponse> updateAccount(String login, UpdateAccountRequest request) {
        log.info("Updating account details for login='{}'", login);
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> {
                    account.setName(request.name());
                    account.setBirthdate(request.birthdate());
                    return accountRepository.save(account);
                })
                .flatMap(savedAccount -> {
                    log.debug("Account saved for login='{}', queuing ACCOUNT_UPDATED outbox event", login);
                    OutboxEvent event = new OutboxEvent(null, "ACCOUNT_UPDATED", savedAccount.getLogin(),
                            "Данные вашего аккаунта были обновлены: " + savedAccount.getName(),
                            LocalDateTime.now(), false, null);
                    return outboxEventRepository.save(event)
                            .doOnSuccess(e -> log.info("Outbox event ACCOUNT_UPDATED saved for login='{}'", login))
                            .then(getAccountInfo(login));
                });
    }

    @Transactional
    public Mono<Void> updateBalance(String login, Long newBalance) {
        log.info("Directly setting balance for login='{}' to {}", login, newBalance);
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> {
                    account.setBalance(newBalance);
                    return accountRepository.save(account);
                })
                .doOnSuccess(saved -> log.debug("Balance updated for login='{}' to {}", login, newBalance))
                .then();
    }

    @Transactional(readOnly = true)
    public Mono<Long> getBalance(String login) {
        log.debug("Fetching balance for login='{}'", login);
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .map(Account::getBalance);
    }

    @Transactional
    public Mono<Long> depositCash(String login, Long amount) {
        log.info("Depositing {} to account login='{}'", amount, login);
        if (amount == null || amount <= 0) {
            log.warn("Deposit rejected for login='{}': invalid amount {}", login, amount);
            return Mono.error(new InvalidAmountException());
        }
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> accountRepository.incrementBalance(login, amount)
                        .flatMap(updated -> {
                            if (updated == 0) {
                                return Mono.error(new AccountNotFoundException(login));
                            }
                            return getBalance(login)
                                    .doOnSuccess(newBalance -> log.info("Deposit successful for login='{}', new balance={}", login, newBalance));
                        }))
                .onErrorMap(
                        e -> e instanceof DataAccessException && !(e instanceof DataIntegrityViolationException)
                                && e.getMessage() != null && e.getMessage().contains("overflow"),
                        e -> {
                            log.warn("Deposit for login='{}' amount={} would cause balance overflow", login, amount);
                            return new InvalidAmountException("Amount would cause balance overflow");
                        }
                );
    }

    @Transactional
    public Mono<Long> withdrawCash(String login, Long amount) {
        log.info("Withdrawing {} from account login='{}'", amount, login);
        if (amount == null || amount <= 0) {
            log.warn("Withdrawal rejected for login='{}': invalid amount {}", login, amount);
            return Mono.error(new InvalidAmountException());
        }
        return accountRepository.decrementBalanceIfSufficient(login, amount)
                .flatMap(updated -> {
                    if (updated == 0) {
                        return accountRepository.findByLogin(login)
                                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                                .flatMap(account -> {
                                    log.warn("Withdrawal rejected for login='{}': insufficient funds, available={}", login, account.getBalance());
                                    return Mono.error(new InsufficientFundsException(account.getBalance()));
                                });
                    }
                    return getBalance(login)
                            .doOnSuccess(newBalance -> log.info("Withdrawal successful for login='{}', new balance={}", login, newBalance));
                });
    }

    @Transactional
    public Mono<TransferResult> transferMoney(String fromLogin, String toLogin, Long amount) {
        log.info("Transfer initiated: from='{}' to='{}' amount={}", fromLogin, toLogin, amount);
        if (amount == null || amount <= 0) {
            log.warn("Transfer rejected: invalid amount {}", amount);
            return Mono.error(new InvalidAmountException());
        }
        if (fromLogin.equals(toLogin)) {
            log.warn("Transfer rejected: sender and recipient are the same login='{}'", fromLogin);
            return Mono.error(new InvalidTransferException("Cannot transfer to yourself"));
        }

        return accountRepository.findByLogin(toLogin)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(toLogin)))
                .flatMap(ignored -> accountRepository.decrementBalanceIfSufficient(fromLogin, amount))
                .flatMap(senderUpdated -> {
                    if (senderUpdated == 0) {
                        return accountRepository.findByLogin(fromLogin)
                                .switchIfEmpty(Mono.error(new AccountNotFoundException(fromLogin)))
                                .flatMap(sender -> {
                                    log.warn("Transfer rejected for sender='{}': insufficient funds, available={}", fromLogin, sender.getBalance());
                                    return Mono.error(new InsufficientFundsException(sender.getBalance()));
                                });
                    }

                    return accountRepository.incrementBalance(toLogin, amount)
                            .flatMap(recipientUpdated -> {
                                if (recipientUpdated == 0) {
                                    return Mono.error(new AccountNotFoundException(toLogin));
                                }

                                return Mono.zip(
                                        accountRepository.findByLogin(fromLogin),
                                        accountRepository.findByLogin(toLogin)
                                ).map(tuple -> {
                                    log.info("Transfer completed: from='{}' (balance={}) to='{}' (balance={}) amount={}",
                                            fromLogin, tuple.getT1().getBalance(),
                                            toLogin, tuple.getT2().getBalance(), amount);
                                    return new TransferResult(
                                            tuple.getT1().getBalance(),
                                            tuple.getT2().getBalance(),
                                            tuple.getT1().getName(),
                                            tuple.getT2().getName()
                                    );
                                });
                            });
                });
    }

    @Transactional
    public Mono<Void> createAccount(CreateAccountRequest request) {
        log.info("Creating new account for login='{}'", request.login());
        return accountRepository.findByLogin(request.login())
                .flatMap(existing -> {
                    log.warn("Account creation failed: login='{}' already exists", request.login());
                    return Mono.<Void>error(new AccountAlreadyExistsException(request.login()));
                })
                .switchIfEmpty(Mono.defer(() ->
                        accountRepository.insertAccount(request.login(), request.name(), request.birthdate())
                                .flatMap(rows -> {
                                    if (rows == 0) {
                                        log.warn("Account insertion returned 0 rows for login='{}', concurrent conflict likely", request.login());
                                        return Mono.error(new AccountAlreadyExistsException(request.login()));
                                    }
                                    log.info("Account created successfully for login='{}'", request.login());
                                    return Mono.empty();
                                })
                ))
                .onErrorMap(
                        e -> e instanceof DataIntegrityViolationException,
                        e -> {
                            log.warn("DataIntegrityViolation on account creation for login='{}': {}", request.login(), e.getMessage());
                            return new AccountAlreadyExistsException(request.login());
                        }
                );
    }

    public record TransferResult(Long senderBalance, Long recipientBalance, String senderName, String recipientName) {}
}
