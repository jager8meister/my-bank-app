package ru.yandex.practicum.accounts.service;

import lombok.RequiredArgsConstructor;
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
public class AccountService {

    private final AccountRepository accountRepository;

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(readOnly = true)
    public Mono<AccountResponse> getAccountInfo(String login) {
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> Mono.zip(
                        accountRepository.findOtherAccounts(login).collectList(),
                        outboxEventRepository.findRecentTransferReceivedByRecipient(login)
                                .map(OutboxEvent::getMessage)
                                .defaultIfEmpty("")
                ).map(tuple -> new AccountResponse(
                        account.getName(),
                        account.getBirthdate(),
                        account.getBalance(),
                        tuple.getT1(),
                        null,
                        tuple.getT2().isEmpty() ? null : tuple.getT2()
                )));
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
                    OutboxEvent event = new OutboxEvent(null, "ACCOUNT_UPDATED", savedAccount.getLogin(),
                            "Данные вашего аккаунта были обновлены: " + savedAccount.getName(),
                            LocalDateTime.now(), false, null);
                    return outboxEventRepository.save(event).then(getAccountInfo(login));
                });
    }

    @Transactional
    public Mono<Void> updateBalance(String login, Long newBalance) {
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> {
                    account.setBalance(newBalance);
                    return accountRepository.save(account);
                })
                .then();
    }

    @Transactional(readOnly = true)
    public Mono<Long> getBalance(String login) {
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .map(Account::getBalance);
    }

    @Transactional
    public Mono<Long> depositCash(String login, Long amount) {
        if (amount == null || amount <= 0) {
            return Mono.error(new InvalidAmountException());
        }
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> accountRepository.incrementBalance(login, amount)
                        .flatMap(updated -> {
                            if (updated == 0) {
                                return Mono.error(new AccountNotFoundException(login));
                            }
                            return getBalance(login);
                        }))
                .onErrorMap(
                        e -> e instanceof DataAccessException && !(e instanceof DataIntegrityViolationException)
                                && e.getMessage() != null && e.getMessage().contains("overflow"),
                        e -> new InvalidAmountException("Amount would cause balance overflow")
                )
                .flatMap(newBalance -> {
                    OutboxEvent event = new OutboxEvent(null, "ACCOUNT_UPDATED", login,
                            "Положено " + amount + " руб. Новый баланс: " + newBalance + " руб",
                            LocalDateTime.now(), false, null);
                    return outboxEventRepository.save(event).thenReturn(newBalance);
                });
    }

    @Transactional
    public Mono<Long> withdrawCash(String login, Long amount) {
        if (amount == null || amount <= 0) {
            return Mono.error(new InvalidAmountException());
        }
        return accountRepository.decrementBalanceIfSufficient(login, amount)
                .flatMap(updated -> {
                    if (updated == 0) {
                        return accountRepository.findByLogin(login)
                                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                                .flatMap(account -> Mono.error(new InsufficientFundsException(account.getBalance())));
                    }
                    return getBalance(login);
                })
                .flatMap(newBalance -> {
                    OutboxEvent event = new OutboxEvent(null, "ACCOUNT_UPDATED", login,
                            "Снято " + amount + " руб. Новый баланс: " + newBalance + " руб",
                            LocalDateTime.now(), false, null);
                    return outboxEventRepository.save(event).thenReturn(newBalance);
                });
    }

    @Transactional
    public Mono<TransferResult> transferMoney(String fromLogin, String toLogin, Long amount) {
        if (amount == null || amount <= 0) {
            return Mono.error(new InvalidAmountException());
        }
        if (fromLogin.equals(toLogin)) {
            return Mono.error(new InvalidTransferException("Cannot transfer to yourself"));
        }

        return accountRepository.findByLogin(toLogin)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(toLogin)))
                .flatMap(ignored -> accountRepository.decrementBalanceIfSufficient(fromLogin, amount))
                .flatMap(senderUpdated -> {
                    if (senderUpdated == 0) {
                        return accountRepository.findByLogin(fromLogin)
                                .switchIfEmpty(Mono.error(new AccountNotFoundException(fromLogin)))
                                .flatMap(sender -> Mono.error(new InsufficientFundsException(sender.getBalance())));
                    }

                    return accountRepository.incrementBalance(toLogin, amount)
                            .flatMap(recipientUpdated -> {
                                if (recipientUpdated == 0) {
                                    return Mono.error(new AccountNotFoundException(toLogin));
                                }

                                return Mono.zip(
                                        accountRepository.findByLogin(fromLogin),
                                        accountRepository.findByLogin(toLogin)
                                ).map(tuple -> new TransferResult(
                                        tuple.getT1().getBalance(),
                                        tuple.getT2().getBalance(),
                                        tuple.getT1().getName(),
                                        tuple.getT2().getName()
                                ));
                            });
                })
                .flatMap(result -> {
                    OutboxEvent senderEvent = new OutboxEvent(null, "TRANSFER_SENT", fromLogin,
                            "Вы перевели " + amount + " руб пользователю " + result.recipientName()
                                    + ". Новый баланс: " + result.senderBalance() + " руб",
                            LocalDateTime.now(), false, null);
                    OutboxEvent recipientEvent = new OutboxEvent(null, "TRANSFER_RECEIVED", toLogin,
                            "Вы получили " + amount + " руб от пользователя " + result.senderName()
                                    + ". Новый баланс: " + result.recipientBalance() + " руб",
                            LocalDateTime.now(), false, null);
                    return Mono.zip(
                            outboxEventRepository.save(senderEvent),
                            outboxEventRepository.save(recipientEvent)
                    ).thenReturn(result);
                });
    }

    @Transactional
    public Mono<Void> createAccount(CreateAccountRequest request) {
        return accountRepository.findByLogin(request.login())
                .flatMap(existing -> Mono.<Void>error(new AccountAlreadyExistsException(request.login())))
                .switchIfEmpty(Mono.defer(() ->
                        accountRepository.insertAccount(request.login(), request.name(), request.birthdate())
                                .flatMap(rows -> rows == 0
                                        ? Mono.error(new AccountAlreadyExistsException(request.login()))
                                        : Mono.empty())
                ))
                .onErrorMap(
                        e -> e instanceof DataIntegrityViolationException,
                        e -> new AccountAlreadyExistsException(request.login())
                );
    }

    public record TransferResult(Long senderBalance, Long recipientBalance, String senderName, String recipientName) {}
}
