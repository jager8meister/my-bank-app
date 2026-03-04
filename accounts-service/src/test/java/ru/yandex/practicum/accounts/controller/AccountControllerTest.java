package ru.yandex.practicum.accounts.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.dto.AccountDto;
import ru.yandex.practicum.accounts.dto.AccountResponse;
import ru.yandex.practicum.accounts.dto.CreateAccountRequest;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.exception.AccountAlreadyExistsException;
import ru.yandex.practicum.accounts.exception.AccountNotFoundException;
import ru.yandex.practicum.accounts.exception.InsufficientFundsException;
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

    @MockitoBean
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
    @DisplayName("GET /api/accounts/{login} - Should reject unauthorized access to other account (403)")
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
    @DisplayName("GET /api/accounts/{login} - Should return 404 when account not found")
    void shouldReturn404WhenAccountNotFound() {
        when(accountService.getAccountInfo("ivanov"))
                .thenReturn(Mono.error(new AccountNotFoundException("ivanov")));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get()
                .uri("/api/accounts/ivanov")
                .exchange()
                .expectStatus().isNotFound();
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
    @DisplayName("PUT /api/accounts/{login} - Should reject update for other user (403)")
    void shouldRejectUpdateForOtherUser() {
        UpdateAccountRequest request = new UpdateAccountRequest(
                "Иван Иванович",
                LocalDate.of(1990, 1, 15)
        );
        when(accountService.updateAccount(eq("petrov"), any(UpdateAccountRequest.class)))
                .thenReturn(Mono.empty());
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .put()
                .uri("/api/accounts/petrov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isForbidden();
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
    @DisplayName("PUT /api/accounts/{login}/balance - Should reject regular user (403)")
    void shouldRejectRegularUserUpdatingBalance() {
        when(accountService.updateBalance("ivanov", 10000L)).thenReturn(Mono.empty());
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .put()
                .uri("/api/accounts/ivanov/balance?balance=10000")
                .exchange()
                .expectStatus().isForbidden();
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
    @DisplayName("GET /api/accounts/{login}/balance - Should reject other user (403)")
    void shouldRejectOtherUserGettingBalance() {
        when(accountService.getBalance("petrov")).thenReturn(Mono.just(3000L));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .get()
                .uri("/api/accounts/petrov/balance")
                .exchange()
                .expectStatus().isForbidden();
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
    @DisplayName("POST /api/accounts/{login}/deposit - Should reject deposit for other user (403)")
    void shouldRejectDepositForOtherUser() {
        when(accountService.depositCash("petrov", 500L)).thenReturn(Mono.just(3500L));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/petrov/deposit?amount=500")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("POST /api/accounts/{login}/deposit - Should return 400 for amount exceeding max")
    void shouldReturn400ForDepositExceedingMaxAmount() {
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/ivanov/deposit?amount=2000000000")
                .exchange()
                .expectStatus().isBadRequest();
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
    @DisplayName("POST /api/accounts/{login}/withdraw - Should return 400 for insufficient funds")
    void shouldReturn400ForInsufficientFunds() {
        when(accountService.withdrawCash("ivanov", 100000L))
                .thenReturn(Mono.error(new InsufficientFundsException(5000L)));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/ivanov/withdraw?amount=100000")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/accounts/{login}/withdraw - Should reject withdraw for other user (403)")
    void shouldRejectWithdrawForOtherUser() {
        when(accountService.withdrawCash("petrov", 500L)).thenReturn(Mono.just(2500L));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/petrov/withdraw?amount=500")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("POST /api/accounts/register - Should create account and return 201")
    void shouldCreateAccountAndReturn201() {
        CreateAccountRequest request = new CreateAccountRequest(
                "newuser",
                "Новый Пользователь",
                LocalDate.of(1995, 6, 15)
        );
        when(accountService.createAccount(any(CreateAccountRequest.class))).thenReturn(Mono.empty());
        webTestClient
                .post()
                .uri("/api/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    @DisplayName("POST /api/accounts/register - Should return 409 when account already exists")
    void shouldReturn409WhenAccountAlreadyExists() {
        CreateAccountRequest request = new CreateAccountRequest(
                "ivanov",
                "Иван Иванов",
                LocalDate.of(1990, 1, 15)
        );
        when(accountService.createAccount(any(CreateAccountRequest.class)))
                .thenReturn(Mono.error(new AccountAlreadyExistsException("ivanov")));
        webTestClient
                .post()
                .uri("/api/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("POST /api/accounts/register - Should return 400 for invalid request body (blank name)")
    void shouldReturn400ForInvalidRequestBody() {
        String invalidJson = "{\"login\":\"newuser\",\"name\":\"\",\"birthdate\":\"1995-06-15\"}";
        webTestClient
                .post()
                .uri("/api/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidJson)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/accounts/register - Should return 400 for under-age user")
    void shouldReturn400ForUnderAgeUser() {
        String invalidJson = "{\"login\":\"younguser\",\"name\":\"Young User\",\"birthdate\":\"2015-01-01\"}";
        webTestClient
                .post()
                .uri("/api/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidJson)
                .exchange()
                .expectStatus().isBadRequest();
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
    @DisplayName("POST /api/accounts/internal/transfer - Should reject regular user authentication (403)")
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

    @Test
    @DisplayName("POST /api/accounts/internal/transfer - Should return 400 for transfer to self")
    void shouldReturn400ForTransferToSelf() {
        when(accountService.transferMoney(eq("ivanov"), eq("ivanov"), anyLong()))
                .thenReturn(Mono.error(new ru.yandex.practicum.accounts.exception.InvalidTransferException("Cannot transfer to yourself")));
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/internal/transfer?from=ivanov&to=ivanov&amount=1000")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/accounts/internal/transfer - Should return 400 for amount exceeding max")
    void shouldReturn400ForTransferExceedingMaxAmount() {
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/internal/transfer?from=ivanov&to=petrov&amount=2000000000")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/accounts/internal/transfer - Should return 400 for insufficient funds")
    void shouldReturn400ForTransferWithInsufficientFunds() {
        when(accountService.transferMoney(anyString(), anyString(), anyLong()))
                .thenReturn(Mono.error(new InsufficientFundsException(100L)));
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/internal/transfer?from=ivanov&to=petrov&amount=10000")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("POST /api/accounts/internal/transfer - Should return 404 when recipient not found")
    void shouldReturn404WhenRecipientNotFound() {
        when(accountService.transferMoney(anyString(), anyString(), anyLong()))
                .thenReturn(Mono.error(new AccountNotFoundException("unknown")));
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/accounts/internal/transfer?from=ivanov&to=unknown&amount=500")
                .exchange()
                .expectStatus().isNotFound();
    }
}
