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
import ru.yandex.practicum.accounts.client.NotificationClient;
import ru.yandex.practicum.accounts.dto.AccountResponse;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.exception.AccountNotFoundException;
import ru.yandex.practicum.accounts.exception.InsufficientFundsException;
import ru.yandex.practicum.accounts.exception.InvalidAmountException;
import ru.yandex.practicum.accounts.exception.InvalidTransferException;
import ru.yandex.practicum.accounts.model.Account;
import ru.yandex.practicum.accounts.repository.AccountRepository;
import ru.yandex.practicum.accounts.util.TestDataFactory;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private AccountService accountService;
    private Account testAccount;
    @BeforeEach
    void setUp() {
        testAccount = TestDataFactory.createIvanovAccount();
        testAccount.setId(1L);
    }
    @Test
    @DisplayName("Should get account info successfully")
    void shouldGetAccountInfo() {
        Account petrov = TestDataFactory.createPetrovAccount();
        Account sidorov = TestDataFactory.createSidorovAccount();
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.findAll()).thenReturn(Flux.just(testAccount, petrov, sidorov));
        Mono<AccountResponse> result = accountService.getAccountInfo("ivanov");
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.name()).isEqualTo("Иван Иванов");
                    assertThat(response.sum()).isEqualTo(5000);
                    assertThat(response.accounts()).hasSize(2);
                    assertThat(response.accounts().get(0).login()).isEqualTo("petrov");
                    assertThat(response.accounts().get(1).login()).isEqualTo("sidorov");
                })
                .verifyComplete();
        verify(accountRepository).findByLogin("ivanov");
        verify(accountRepository).findAll();
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
    @DisplayName("Should update account successfully")
    void shouldUpdateAccountSuccessfully() {
        UpdateAccountRequest request = new UpdateAccountRequest(
                "Иван Иванович Иванов",
                LocalDate.of(1990, 1, 15)
        );
        Account petrov = TestDataFactory.createPetrovAccount();
        Account sidorov = TestDataFactory.createSidorovAccount();
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(testAccount));
        when(notificationClient.sendAccountUpdatedNotification(anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(accountRepository.findAll()).thenReturn(Flux.just(testAccount, petrov, sidorov));
        Mono<AccountResponse> result = accountService.updateAccount("ivanov", request);
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.name()).isEqualTo("Иван Иванович Иванов");
                })
                .verifyComplete();
        verify(accountRepository, times(2)).findByLogin("ivanov");
        verify(accountRepository).save(any(Account.class));
        verify(notificationClient).sendAccountUpdatedNotification(eq("ivanov"), anyString());
    }
    @Test
    @DisplayName("Should update balance successfully")
    void shouldUpdateBalanceSuccessfully() {
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.save(any(Account.class))).thenReturn(Mono.just(testAccount));
        Mono<Void> result = accountService.updateBalance("ivanov", 10000);
        StepVerifier.create(result)
                .verifyComplete();
        verify(accountRepository).findByLogin("ivanov");
        verify(accountRepository).save(any(Account.class));
    }
    @Test
    @DisplayName("Should get balance successfully")
    void shouldGetBalanceSuccessfully() {
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        Mono<Integer> result = accountService.getBalance("ivanov");
        StepVerifier.create(result)
                .expectNext(5000)
                .verifyComplete();
    }
    @Test
    @DisplayName("Should deposit cash successfully")
    void shouldDepositCashSuccessfully() {
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.incrementBalance("ivanov", 500)).thenReturn(Mono.just(1));
        testAccount.setBalance(5500);
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));

        Mono<Integer> result = accountService.depositCash("ivanov", 500);

        StepVerifier.create(result)
                .expectNext(5500)
                .verifyComplete();
        verify(accountRepository).incrementBalance("ivanov", 500);
    }
    @Test
    @DisplayName("Should reject deposit with negative amount")
    void shouldRejectDepositWithNegativeAmount() {
        Mono<Integer> result = accountService.depositCash("ivanov", -500);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }
    @Test
    @DisplayName("Should withdraw cash successfully")
    void shouldWithdrawCashSuccessfully() {
        when(accountRepository.decrementBalanceIfSufficient("ivanov", 500)).thenReturn(Mono.just(1));
        testAccount.setBalance(4500);
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));

        Mono<Integer> result = accountService.withdrawCash("ivanov", 500);

        StepVerifier.create(result)
                .expectNext(4500)
                .verifyComplete();
        verify(accountRepository).decrementBalanceIfSufficient("ivanov", 500);
    }
    @Test
    @DisplayName("Should reject withdrawal with insufficient funds")
    void shouldRejectWithdrawalWithInsufficientFunds() {
        when(accountRepository.decrementBalanceIfSufficient("ivanov", 10000)).thenReturn(Mono.just(0));
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));

        Mono<Integer> result = accountService.withdrawCash("ivanov", 10000);

        StepVerifier.create(result)
                .expectError(InsufficientFundsException.class)
                .verify();
        verify(accountRepository).decrementBalanceIfSufficient("ivanov", 10000);
    }
    @Test
    @DisplayName("Should transfer money successfully")
    void shouldTransferMoneySuccessfully() {
        Account petrov = TestDataFactory.createPetrovAccount();
        petrov.setId(2L);

        testAccount.setBalance(5000);  // initial balance
        petrov.setBalance(3000);  // initial balance

        // First call - verify both accounts exist and check for overflow
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.findByLogin("petrov")).thenReturn(Mono.just(petrov));

        // Atomic operations
        when(accountRepository.decrementBalanceIfSufficient("ivanov", 1000)).thenReturn(Mono.just(1));
        when(accountRepository.incrementBalance("petrov", 1000)).thenReturn(Mono.just(1));

        // Final call - fetch updated balances
        testAccount.setBalance(4000);  // after transfer
        petrov.setBalance(4000);  // after transfer

        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "petrov", 1000);

        StepVerifier.create(result)
                .assertNext(transferResult -> {
                    assertThat(transferResult.senderBalance()).isEqualTo(4000);
                    assertThat(transferResult.recipientBalance()).isEqualTo(4000);
                    assertThat(transferResult.senderName()).isEqualTo("Иван Иванов");
                    assertThat(transferResult.recipientName()).isEqualTo("Петр Петров");
                })
                .verifyComplete();
        verify(accountRepository).decrementBalanceIfSufficient("ivanov", 1000);
        verify(accountRepository).incrementBalance("petrov", 1000);
    }
    @Test
    @DisplayName("Should reject transfer with insufficient funds")
    void shouldRejectTransferWithInsufficientFunds() {
        Account petrov = TestDataFactory.createPetrovAccount();
        petrov.setId(2L);

        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.findByLogin("petrov")).thenReturn(Mono.just(petrov));
        when(accountRepository.decrementBalanceIfSufficient("ivanov", 10000)).thenReturn(Mono.just(0));

        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "petrov", 10000);

        StepVerifier.create(result)
                .expectError(InsufficientFundsException.class)
                .verify();
        verify(accountRepository).decrementBalanceIfSufficient("ivanov", 10000);
        verify(accountRepository, never()).incrementBalance(any(), any());
    }
    @Test
    @DisplayName("Should reject transfer to same account")
    void shouldRejectTransferToSameAccount() {
        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "ivanov", 1000);
        StepVerifier.create(result)
                .expectError(InvalidTransferException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }
    @Test
    @DisplayName("Should reject transfer with negative amount")
    void shouldRejectTransferWithNegativeAmount() {
        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "petrov", -500);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    @DisplayName("Should reject deposit with null amount")
    void shouldRejectDepositWithNullAmount() {
        Mono<Integer> result = accountService.depositCash("ivanov", null);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    @DisplayName("Should reject deposit with zero amount")
    void shouldRejectDepositWithZeroAmount() {
        Mono<Integer> result = accountService.depositCash("ivanov", 0);
        StepVerifier.create(result)
                .expectError(InvalidAmountException.class)
                .verify();
        verify(accountRepository, never()).findByLogin(any());
    }

    @Test
    @DisplayName("Should reject withdrawal when account not found")
    void shouldRejectWithdrawalWhenAccountNotFound() {
        when(accountRepository.decrementBalanceIfSufficient("unknown", 500)).thenReturn(Mono.just(0));
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());

        Mono<Integer> result = accountService.withdrawCash("unknown", 500);

        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
        verify(accountRepository).decrementBalanceIfSufficient("unknown", 500);
    }

    @Test
    @DisplayName("Should reject transfer when sender not found")
    void shouldRejectTransferWhenSenderNotFound() {
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        when(accountRepository.findByLogin("petrov")).thenReturn(Mono.just(TestDataFactory.createPetrovAccount()));
        Mono<AccountService.TransferResult> result = accountService.transferMoney("unknown", "petrov", 1000);
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject transfer when recipient not found")
    void shouldRejectTransferWhenRecipientNotFound() {
        when(accountRepository.findByLogin("ivanov")).thenReturn(Mono.just(testAccount));
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        Mono<AccountService.TransferResult> result = accountService.transferMoney("ivanov", "unknown", 1000);
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject update when account not found")
    void shouldRejectUpdateWhenAccountNotFound() {
        UpdateAccountRequest request = new UpdateAccountRequest("Test Name", LocalDate.now());
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        Mono<AccountResponse> result = accountService.updateAccount("unknown", request);
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject balance update when account not found")
    void shouldRejectBalanceUpdateWhenAccountNotFound() {
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        Mono<Void> result = accountService.updateBalance("unknown", 10000);
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject getBalance when account not found")
    void shouldRejectGetBalanceWhenAccountNotFound() {
        when(accountRepository.findByLogin("unknown")).thenReturn(Mono.empty());
        Mono<Integer> result = accountService.getBalance("unknown");
        StepVerifier.create(result)
                .expectError(AccountNotFoundException.class)
                .verify();
    }
}
