package ru.yandex.practicum.accounts.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import org.springframework.dao.DataIntegrityViolationException;
import ru.yandex.practicum.accounts.client.NotificationClient;
import ru.yandex.practicum.accounts.dto.AccountResponse;
import ru.yandex.practicum.accounts.dto.CreateAccountRequest;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.exception.AccountAlreadyExistsException;
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

    @Transactional(readOnly = true)
    public Mono<AccountResponse> getAccountInfo(String login) {
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> accountRepository.findOtherAccounts(login)
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

    @Transactional
    public Mono<AccountResponse> updateAccount(String login, UpdateAccountRequest request) {
        return accountRepository.findByLogin(login)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(login)))
                .flatMap(account -> {
                    account.setName(request.name());
                    account.setBirthdate(request.birthdate());
                    return accountRepository.save(account);
                })
                .flatMap(savedAccount -> notificationClient.sendAccountUpdatedNotification(
                        savedAccount.getLogin(),
                        "Данные вашего аккаунта были обновлены: " + savedAccount.getName()
                ).then(getAccountInfo(login)));
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
                .flatMap(account -> {
                    if (amount > Long.MAX_VALUE - account.getBalance()) {
                        return Mono.error(new InvalidAmountException("Amount would cause balance overflow"));
                    }
                    return accountRepository.incrementBalance(login, amount)
                            .flatMap(updated -> {
                                if (updated == 0) {
                                    return Mono.error(new AccountNotFoundException(login));
                                }
                                return getBalance(login);
                            });
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

        // Validate recipient exists before touching sender balance
        return accountRepository.findByLogin(toLogin)
                .switchIfEmpty(Mono.error(new AccountNotFoundException(toLogin)))
                .flatMap(ignored -> accountRepository.decrementBalanceIfSufficient(fromLogin, amount))
                .flatMap(senderUpdated -> {
                    if (senderUpdated == 0) {
                        // Re-query actual current balance — upfront stale read is unsafe under concurrency
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
