package ru.yandex.practicum.accounts.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.dto.AccountDto;
import ru.yandex.practicum.accounts.dto.AccountResponse;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.service.AccountService;
import ru.yandex.practicum.accounts.util.SecurityTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AccountController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AccountController WebFlux Tests")
class AccountControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AccountService accountService;

    @Test
    @DisplayName("GET /api/accounts/{login} - Should return account info for authorized user")
    void shouldReturnAccountInfoForAuthorizedUser() {
        AccountResponse response = new AccountResponse(
                "Иван Иванов",
                LocalDate.of(1990, 1, 15),
                5000L,
                List.of(new AccountDto("petrov", "Петр Петров")),
                null,
                null
        );
        when(accountService.getAccountInfo("ivanov")).thenReturn(Mono.just(response));
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
                .jsonPath("$.sum").isEqualTo(5000)
                .jsonPath("$.accounts[0].login").isEqualTo("petrov");
    }

    @Test
    @DisplayName("GET /api/accounts/{login} - Should reject unauthorized access to other account")
    void shouldRejectUnauthorizedAccessToOtherAccount() {
        when(accountService.getAccountInfo("petrov")).thenReturn(Mono.empty());
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get()
                .uri("/api/accounts/petrov")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("GET /api/accounts/{login} - Service account can access any account")
    void serviceAccountCanAccessAnyAccount() {
        AccountResponse response = new AccountResponse(
                "Петр Петров",
                LocalDate.of(1985, 5, 20),
                3000L,
                List.of(),
                null,
                null
        );
        when(accountService.getAccountInfo("petrov")).thenReturn(Mono.just(response));
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get()
                .uri("/api/accounts/petrov")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Петр Петров")
                .jsonPath("$.sum").isEqualTo(3000);
    }

    @Test
    @DisplayName("PUT /api/accounts/{login} - Should update account for authorized user")
    void shouldUpdateAccountForAuthorizedUser() {
        UpdateAccountRequest request = new UpdateAccountRequest(
                "Иван Иванович",
                LocalDate.of(1990, 1, 15)
        );
        AccountResponse response = new AccountResponse(
                "Иван Иванович",
                LocalDate.of(1990, 1, 15),
                5000L,
                List.of(),
                null,
                null
        );
        when(accountService.updateAccount(eq("ivanov"), any(UpdateAccountRequest.class)))
                .thenReturn(Mono.just(response));
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
                .jsonPath("$.name").isEqualTo("Иван Иванович");
    }

    @Test
    @DisplayName("PUT /api/accounts/{login}/balance - Should update balance with microservice auth")
    void shouldUpdateBalance() {
        when(accountService.updateBalance("ivanov", 10000L)).thenReturn(Mono.empty());
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .put()
                .uri("/api/accounts/ivanov/balance?balance=10000")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("GET /api/accounts/{login}/balance - Should return balance")
    void shouldReturnBalance() {
        when(accountService.getBalance("ivanov")).thenReturn(Mono.just(5000L));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get()
                .uri("/api/accounts/ivanov/balance")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(5000L);
    }

    @Test
    @DisplayName("POST /api/accounts/{login}/deposit - Should deposit cash")
    void shouldDepositCash() {
        when(accountService.depositCash("ivanov", 500L)).thenReturn(Mono.just(5500L));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/ivanov/deposit?amount=500")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(5500L);
    }

    @Test
    @DisplayName("POST /api/accounts/{login}/withdraw - Should withdraw cash")
    void shouldWithdrawCash() {
        when(accountService.withdrawCash("ivanov", 500L)).thenReturn(Mono.just(4500L));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/ivanov/withdraw?amount=500")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Long.class)
                .isEqualTo(4500L);
    }

    @Test
    @DisplayName("POST /api/accounts/internal/transfer - Should transfer money with microservice auth")
    void shouldTransferMoney() {
        AccountService.TransferResult result = new AccountService.TransferResult(
                4000L, 4000L, "Иван Иванов", "Петр Петров"
        );
        when(accountService.transferMoney("ivanov", "petrov", 1000L))
                .thenReturn(Mono.just(result));
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/internal/transfer?from=ivanov&to=petrov&amount=1000")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.senderBalance").isEqualTo(4000)
                .jsonPath("$.recipientBalance").isEqualTo(4000);
    }

    @Test
    @DisplayName("POST /api/accounts/internal/transfer - Should reject user authentication")
    void shouldRejectUnauthorizedTransfer() {
        when(accountService.transferMoney("petrov", "sidorov", 1000L)).thenReturn(Mono.empty());
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/internal/transfer?from=petrov&to=sidorov&amount=1000")
                .exchange()
                .expectStatus().isForbidden();
    }
}
