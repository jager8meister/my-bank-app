package ru.yandex.practicum.accounts.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import ru.yandex.practicum.accounts.AbstractIntegrationTest;
import ru.yandex.practicum.accounts.dto.NotificationEvent;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.model.Account;
import ru.yandex.practicum.accounts.repository.AccountRepository;
import ru.yandex.practicum.accounts.util.SecurityTestUtils;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Accounts Service Integration Tests")
class AccountsServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private AccountRepository accountRepository;

    @MockitoBean
    private KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    @Test
    @DisplayName("Should get account info from database")
    void shouldGetAccountInfoFromDatabase() {
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get()
                .uri("/api/accounts/ivanov")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.name").isEqualTo("Иван Иванов")
                .jsonPath("$.birthdate").isEqualTo("1990-01-15")
                .jsonPath("$.sum").isEqualTo(5000)
                .jsonPath("$.accounts").isArray()
                .jsonPath("$.accounts.length()").isEqualTo(2);
    }

    @Test
    @DisplayName("Should update account in database")
    void shouldUpdateAccountInDatabase() {
        UpdateAccountRequest request = new UpdateAccountRequest(
                "Иван Иванович Иванов",
                LocalDate.of(1990, 2, 20)
        );
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .put()
                .uri("/api/accounts/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Иван Иванович Иванов")
                .jsonPath("$.birthdate").isEqualTo("1990-02-20");
        Account updatedAccount = accountRepository.findByLogin("ivanov").block();
        assertThat(updatedAccount).isNotNull();
        assertThat(updatedAccount.getName()).isEqualTo("Иван Иванович Иванов");
        assertThat(updatedAccount.getBirthdate()).isEqualTo(LocalDate.of(1990, 2, 20));
    }

    @Test
    @DisplayName("Should deposit cash and update balance in database")
    void shouldDepositCashAndUpdateBalance() {
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        Long initialBalance = accountRepository.findByLogin("ivanov")
                .map(Account::getBalance)
                .block();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/ivanov/deposit?amount=1000")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(initialBalance + 1000L);
        Long newBalance = accountRepository.findByLogin("ivanov")
                .map(Account::getBalance)
                .block();
        assertThat(newBalance).isEqualTo(initialBalance + 1000L);
    }

    @Test
    @DisplayName("Should withdraw cash and update balance in database")
    void shouldWithdrawCashAndUpdateBalance() {
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        Long initialBalance = accountRepository.findByLogin("ivanov")
                .map(Account::getBalance)
                .block();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/ivanov/withdraw?amount=500")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(initialBalance - 500L);
        Long newBalance = accountRepository.findByLogin("ivanov")
                .map(Account::getBalance)
                .block();
        assertThat(newBalance).isEqualTo(initialBalance - 500L);
    }

    @Test
    @DisplayName("Should reject withdrawal with insufficient funds")
    void shouldRejectWithdrawalWithInsufficientFunds() {
        Authentication auth = SecurityTestUtils.createUserAuthentication("sidorov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/sidorov/withdraw?amount=2000")
                .exchange()
                .expectStatus().is5xxServerError();
        Long balance = accountRepository.findByLogin("sidorov")
                .map(Account::getBalance)
                .block();
        assertThat(balance).isEqualTo(1000L);
    }

    @Test
    @DisplayName("Should transfer money between accounts in database")
    void shouldTransferMoneyBetweenAccounts() {
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        Long ivanovInitialBalance = accountRepository.findByLogin("ivanov")
                .map(Account::getBalance)
                .block();
        Long petrovInitialBalance = accountRepository.findByLogin("petrov")
                .map(Account::getBalance)
                .block();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/internal/transfer?from=ivanov&to=petrov&amount=500")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.senderBalance").isEqualTo(ivanovInitialBalance - 500)
                .jsonPath("$.recipientBalance").isEqualTo(petrovInitialBalance + 500);
        Long ivanovNewBalance = accountRepository.findByLogin("ivanov")
                .map(Account::getBalance)
                .block();
        Long petrovNewBalance = accountRepository.findByLogin("petrov")
                .map(Account::getBalance)
                .block();
        assertThat(ivanovNewBalance).isEqualTo(ivanovInitialBalance - 500L);
        assertThat(petrovNewBalance).isEqualTo(petrovInitialBalance + 500L);
    }

    @Test
    @DisplayName("Should enforce authorization - user can only access own account")
    void shouldEnforceAuthorizationUserCanOnlyAccessOwnAccount() {
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get()
                .uri("/api/accounts/petrov")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("Should allow service account to access any account")
    void shouldAllowServiceAccountToAccessAnyAccount() {
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get()
                .uri("/api/accounts/petrov")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Петр Петров");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get()
                .uri("/api/accounts/sidorov")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Сидор Сидоров");
    }

    @Test
    @DisplayName("Should return 404 for non-existent account")
    void shouldReturn404ForNonExistentAccount() {
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get()
                .uri("/api/accounts/nonexistent")
                .exchange()
                .expectStatus().is5xxServerError();
    }
}
