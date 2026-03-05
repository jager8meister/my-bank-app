package ru.yandex.practicum.accounts.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
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
import ru.yandex.practicum.accounts.util.TestDataFactory;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private AccountService accountService;

    private Account testAccount;

    @BeforeEach
    void setUp() {
        testAccount = TestDataFactory.createIvanovAccount();
        testAccount.setId(1L);
        lenient().when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenReturn(Mono.just(new OutboxEvent()));
    }

    @Test
    @DisplayName("Should get account info successfully")
    void shouldGetAccountInfo() {
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.findOtherAccounts("ivanov")).thenReturn(Flux.just(
                TestDataFactory.createAccountDto("petrov", "Петр Петров"),
                TestDataFactory.createAccountDto("sidorov", "Сидор Сидоров")
        ));
        Mono<AccountResponse> result = accountService.getAccountInfo("ivanov");
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.name()).isEqualTo("Иван Иванов");
                    assertThat(response.sum()).isEqualTo(5000L);
                    assertThat(response.accounts()).hasSize(2);
                    assertThat(response.accounts().get(0).login()).isEqualTo("petrov");
                    assertThat(response.accounts().get(1).login()).isEqualTo("sidorov");
                })
                .verifyComplete();
        verify(accountRepository).findByLogin("ivanov");
        verify(accountRepository).findOtherAccounts("ivanov");
    }

    @Test
    @DisplayName("Should throw error when account not found for getAccountInfo")
    void shouldThrowErrorWhenAccountNotFoundForGetAccountInfo() {
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        Mono<AccountResponse> result = accountService.getAccountInfo("unknown");
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("Should deposit cash successfully")
    void shouldDepositCashSuccessfully() {
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.incrementBalance("ivanov", 500L)).thenReturn(Mono.just(1));
        testAccount.setBalance(5500L);
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));

        Mono<Long> result = accountService.depositCash("ivanov", 500L);

        StepVerifier.create(result)
                .expectNext(5500L)
                .verifyComplete();
        verify(accountRepository).incrementBalance("ivanov", 500L);
    }

    @Test
    @DisplayName("Should reject deposit with negative amount")
    void shouldRejectDepositWithNegativeAmount() {
        Mono<Long> result = accountService.depositCash("ivanov", -500L);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    @DisplayName("Should reject deposit with null amount")
    void shouldRejectDepositWithNullAmount() {
        Mono<Long> result = accountService.depositCash("ivanov", null);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    @DisplayName("Should reject deposit with zero amount")
    void shouldRejectDepositWithZeroAmount() {
        Mono<Long> result = accountService.depositCash("ivanov", 0L);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when account not found during deposit")
    void shouldThrowAccountNotFoundWhenDepositForUnknownAccount() {
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        Mono<Long> result = accountService.depositCash("unknown", 500L);
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
        verify(accountRepository, never()).incrementBalance(any(), any());
    }

    @Test
    @DisplayName("Should withdraw cash successfully")
    void shouldWithdrawCashSuccessfully() {
        when(accountRepository.decrementBalanceIfSufficient("ivanov", 500L)).thenReturn(Mono.just(1));
        testAccount.setBalance(4500L);
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));

        Mono<Long> result = accountService.withdrawCash("ivanov", 500L);

        StepVerifier.create(result)
                .expectNext(4500L)
                .verifyComplete();
        verify(accountRepository).decrementBalanceIfSufficient("ivanov", 500L);
    }

    @Test
    @DisplayName("Should reject withdrawal with insufficient funds")
    void shouldRejectWithdrawalWithInsufficientFunds() {
        when(accountRepository.decrementBalanceIfSufficient("ivanov", 10000L)).thenReturn(Mono.just(0));
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));

        Mono<Long> result = accountService.withdrawCash("ivanov", 10000L);

        StepVerifier.create(result)
                .expectError(InsufficientFundsException.class)
                .verify();
        verify(accountRepository).decrementBalanceIfSufficient("ivanov", 10000L);
    }

    @Test
    @DisplayName("Should reject withdrawal when account not found")
    void shouldRejectWithdrawalWhenAccountNotFound() {
        when(accountRepository.decrementBalanceIfSufficient("unknown", 500L)).thenReturn(Mono.just(0));
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());

        Mono<Long> result = accountService.withdrawCash("unknown", 500L);

        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
        verify(accountRepository).decrementBalanceIfSufficient("unknown", 500L);
    }

    @Test
    @DisplayName("Should reject withdrawal with negative amount")
    void shouldRejectWithdrawalWithNegativeAmount() {
        Mono<Long> result = accountService.withdrawCash("ivanov", -100L);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).decrementBalanceIfSufficient(any(), any());
    }

    @Test
    @DisplayName("Should reject withdrawal with zero amount")
    void shouldRejectWithdrawalWithZeroAmount() {
        Mono<Long> result = accountService.withdrawCash("ivanov", 0L);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).decrementBalanceIfSufficient(any(), any());
    }

    @Test
    @DisplayName("Should transfer money successfully")
    void shouldTransferMoneySuccessfully() {
        Account petrov = TestDataFactory.createPetrovAccount();
        petrov.setId(2L);

        testAccount.setBalance(5000L);
        petrov.setBalance(3000L);

        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.findByLogin("petrov")).thenReturn(Mono.just(petrov));

        when(accountRepository.decrementBalanceIfSufficient("ivanov", 1000L)).thenReturn(Mono.just(1));
        when(accountRepository.incrementBalance("petrov", 1000L)).thenReturn(Mono.just(1));

        testAccount.setBalance(4000L);
        petrov.setBalance(4000L);

        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "petrov", 1000L);

        StepVerifier.create(result)
                .assertNext(transferResult -> {
                    assertThat(transferResult.senderBalance()).isEqualTo(4000L);
                    assertThat(transferResult.recipientBalance()).isEqualTo(4000L);
                    assertThat(transferResult.senderName()).isEqualTo("Иван Иванов");
                    assertThat(transferResult.recipientName()).isEqualTo("Петр Петров");
                })
                .verifyComplete();
        verify(accountRepository).decrementBalanceIfSufficient("ivanov", 1000L);
        verify(accountRepository).incrementBalance("petrov", 1000L);
    }

    @Test
    @DisplayName("Should reject transfer with insufficient funds")
    void shouldRejectTransferWithInsufficientFunds() {
        Account petrov = TestDataFactory.createPetrovAccount();
        petrov.setId(2L);

        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.findByLogin("petrov")).thenReturn(Mono.just(petrov));
        when(accountRepository.decrementBalanceIfSufficient("ivanov", 10000L)).thenReturn(Mono.just(0));

        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "petrov", 10000L);

        StepVerifier.create(result)
                .expectError(InsufficientFundsException.class)
                .verify();
        verify(accountRepository).decrementBalanceIfSufficient("ivanov", 10000L);
        verify(accountRepository, never()).incrementBalance(any(), any());
    }

    @Test
    @DisplayName("Should reject transfer to same account")
    void shouldRejectTransferToSameAccount() {
        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "ivanov", 1000L);
        StepVerifier.create(result)
                .expectError(InvalidTransferException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    @DisplayName("Should reject transfer with negative amount")
    void shouldRejectTransferWithNegativeAmount() {
        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "petrov", -500L);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    @DisplayName("Should reject transfer with zero amount")
    void shouldRejectTransferWithZeroAmount() {
        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "petrov", 0L);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    @DisplayName("Should reject transfer with null amount")
    void shouldRejectTransferWithNullAmount() {
        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "petrov", null);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    @DisplayName("Should reject transfer when sender not found")
    void shouldRejectTransferWhenSenderNotFound() {
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        when(accountRepository.findByLogin("petrov")).thenReturn(Mono.just(TestDataFactory.createPetrovAccount()));
        when(accountRepository.decrementBalanceIfSufficient("unknown", 1000L)).thenReturn(Mono.just(0));
        Mono<AccountService.TransferResult> result = accountService.transferMoney("unknown", "petrov", 1000L);
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
        verify(accountRepository, never()).save(any());
        verify(accountRepository, never()).incrementBalance(any(), any());
    }

    @Test
    @DisplayName("Should reject transfer when recipient not found")
    void shouldRejectTransferWhenRecipientNotFound() {
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "unknown", 1000L);
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
        verify(accountRepository, never()).save(any());
        verify(accountRepository, never()).decrementBalanceIfSufficient(any(), any());
    }

    @Test
    @DisplayName("Should create account successfully")
    void shouldCreateAccountSuccessfully() {
        CreateAccountRequest request = new CreateAccountRequest(
                "newuser",
                "Новый Пользователь",
                LocalDate.of(1995, 6, 15)
        );
        when(accountRepository.findByLogin("newuser")).thenReturn(Mono.empty());
        when(accountRepository.insertAccount(eq("newuser"), eq("Новый Пользователь"), eq(LocalDate.of(1995, 6, 15))))
                .thenReturn(Mono.just(1));

        Mono<Void> result = accountService.createAccount(request);

        StepVerifier.create(result)
                .verifyComplete();
        verify(accountRepository).findByLogin("newuser");
        verify(accountRepository).insertAccount("newuser", "Новый Пользователь", LocalDate.of(1995, 6, 15));
    }

    @Test
    @DisplayName("Should reject createAccount when account already exists")
    void shouldRejectCreateAccountWhenAlreadyExists() {
        CreateAccountRequest request = new CreateAccountRequest(
                "ivanov",
                "Иван Иванов",
                LocalDate.of(1990, 1, 15)
        );
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));

        Mono<Void> result = accountService.createAccount(request);

        StepVerifier.create(result)
                .expectError(AccountAlreadyExistsException.class)
                .verify();
        verify(accountRepository).findByLogin("ivanov");
        verify(accountRepository, never()).insertAccount(any(), any(), any());
    }

    @Test
    @DisplayName("Should reject createAccount when insertAccount returns 0 rows (race condition)")
    void shouldRejectCreateAccountWhenInsertReturnsZeroRows() {
        CreateAccountRequest request = new CreateAccountRequest(
                "newuser",
                "Новый Пользователь",
                LocalDate.of(1995, 6, 15)
        );
        when(accountRepository.findByLogin("newuser")).thenReturn(Mono.empty());
        when(accountRepository.insertAccount(eq("newuser"), eq("Новый Пользователь"), eq(LocalDate.of(1995, 6, 15))))
                .thenReturn(Mono.just(0));

        Mono<Void> result = accountService.createAccount(request);

        StepVerifier.create(result)
                .expectError(AccountAlreadyExistsException.class)
                .verify();
    }

    @Test
    @DisplayName("Should update account successfully and write outbox event")
    void shouldUpdateAccountSuccessfully() {
        UpdateAccountRequest request = new UpdateAccountRequest(
                "Иван Иванович Иванов",
                LocalDate.of(1990, 1, 15)
        );
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(testAccount));
        when(accountRepository.findOtherAccounts("ivanov")).thenReturn(Flux.just(
                TestDataFactory.createAccountDto("petrov", "Петр Петров"),
                TestDataFactory.createAccountDto("sidorov", "Сидор Сидоров")
        ));
        Mono<AccountResponse> result = accountService.updateAccount("ivanov", request);
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.name()).isEqualTo("Иван Иванович Иванов");
                })
                .verifyComplete();
        verify(accountRepository, times(2)).findByLogin("ivanov");
        verify(accountRepository).save(any(Account.class));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should reject update when account not found")
    void shouldRejectUpdateWhenAccountNotFound() {
        UpdateAccountRequest request = new UpdateAccountRequest("Test Name", LocalDate.of(1990, 1, 1));
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        Mono<AccountResponse> result = accountService.updateAccount("unknown", request);
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update balance successfully")
    void shouldUpdateBalanceSuccessfully() {
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(testAccount));
        Mono<Void> result = accountService.updateBalance("ivanov", 10000L);
        StepVerifier.create(result)
                .verifyComplete();
        verify(accountRepository).findByLogin("ivanov");
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("Should reject balance update when account not found")
    void shouldRejectBalanceUpdateWhenAccountNotFound() {
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        Mono<Void> result = accountService.updateBalance("unknown", 10000L);
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should get balance successfully")
    void shouldGetBalanceSuccessfully() {
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        Mono<Long> result = accountService.getBalance("ivanov");
        StepVerifier.create(result)
                .expectNext(5000L)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should reject getBalance when account not found")
    void shouldRejectGetBalanceWhenAccountNotFound() {
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        Mono<Long> result = accountService.getBalance("unknown");
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
    }
}
