package ru.yandex.practicum.accounts.exception;

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
import ru.yandex.practicum.accounts.controller.AccountController;
import ru.yandex.practicum.accounts.controller.TestSecurityConfig;
import ru.yandex.practicum.accounts.dto.CreateAccountRequest;
import ru.yandex.practicum.accounts.service.AccountService;
import ru.yandex.practicum.accounts.util.SecurityTestUtils;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = AccountController.class)
@Import(TestSecurityConfig.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private AccountService accountService;

    private Authentication userAuth() {
        return SecurityTestUtils.createUserAuthentication("ivanov");
    }

    private Authentication serviceAuth() {
        return SecurityTestUtils.createServiceAuthentication();
    }

    @Test
    @DisplayName("AccountNotFoundException maps to 404 Not Found")
    void accountNotFoundExceptionMapsTo404() {
        when(accountService.getAccountInfo("ivanov"))
                .thenReturn(Mono.error(new AccountNotFoundException("ivanov")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(userAuth()))
                .get()
                .uri("/api/accounts/ivanov")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("Not Found")
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    @DisplayName("AccountAlreadyExistsException maps to 409 Conflict")
    void accountAlreadyExistsExceptionMapsTo409() {
        when(accountService.createAccount(any(CreateAccountRequest.class)))
                .thenReturn(Mono.error(new AccountAlreadyExistsException("ivanov")));

        CreateAccountRequest request = new CreateAccountRequest(
                "ivanov",
                "Иван Иванов",
                LocalDate.of(1990, 1, 15)
        );

        webTestClient
                .post()
                .uri("/api/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.status").isEqualTo(409)
                .jsonPath("$.error").isEqualTo("Conflict")
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    @DisplayName("InsufficientFundsException maps to 400 Bad Request")
    void insufficientFundsExceptionMapsTo400() {
        when(accountService.withdrawCash("ivanov", 100000L))
                .thenReturn(Mono.error(new InsufficientFundsException(500L)));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(userAuth()))
                .post()
                .uri("/api/accounts/ivanov/withdraw?amount=100000")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.error").isEqualTo("Bad Request")
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    @DisplayName("InvalidAmountException maps to 400 Bad Request")
    void invalidAmountExceptionMapsTo400() {
        when(accountService.depositCash("ivanov", 500L))
                .thenReturn(Mono.error(new InvalidAmountException()));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(userAuth()))
                .post()
                .uri("/api/accounts/ivanov/deposit?amount=500")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    @DisplayName("InvalidTransferException maps to 400 Bad Request")
    void invalidTransferExceptionMapsTo400() {
        when(accountService.transferMoney(anyString(), anyString(), anyLong()))
                .thenReturn(Mono.error(new InvalidTransferException("Cannot transfer to yourself")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(serviceAuth()))
                .post()
                .uri("/api/accounts/internal/transfer?from=ivanov&to=ivanov&amount=1000")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").isEqualTo("Cannot transfer to yourself");
    }

    @Test
    @DisplayName("ForbiddenException maps to 403 Forbidden")
    void forbiddenExceptionMapsTo403() {
        when(accountService.getAccountInfo("petrov")).thenReturn(Mono.empty());
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(userAuth()))
                .get()
                .uri("/api/accounts/petrov")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("Forbidden");
    }

    @Test
    @DisplayName("Unexpected RuntimeException maps to 500 Internal Server Error")
    void unexpectedExceptionMapsTo500() {
        when(accountService.getAccountInfo("ivanov"))
                .thenReturn(Mono.error(new RuntimeException("Unexpected database error")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(userAuth()))
                .get()
                .uri("/api/accounts/ivanov")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.status").isEqualTo(500)
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    @DisplayName("ConstraintViolationException (invalid path param) maps to 400 Bad Request")
    void constraintViolationExceptionMapsTo400() {
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(userAuth()))
                .get()
                .uri("/api/accounts/thisloginiswaytoolongtobevalid")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @DisplayName("WebExchangeBindException (invalid request body) maps to 400 Bad Request")
    void webExchangeBindExceptionMapsTo400() {
        String invalidJson = "{\"login\":\"validuser\",\"name\":\"\",\"birthdate\":\"1990-01-01\"}";
        webTestClient
                .post()
                .uri("/api/accounts/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(invalidJson)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.message").value(msg -> {
                    assert msg.toString().contains("Validation") || msg.toString().contains("validation")
                            || msg.toString().contains("name");
                });
    }

    @Test
    @DisplayName("Error response always contains timestamp field")
    void errorResponseContainsTimestamp() {
        when(accountService.getAccountInfo("ivanov"))
                .thenReturn(Mono.error(new AccountNotFoundException("ivanov")));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(userAuth()))
                .get()
                .uri("/api/accounts/ivanov")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.timestamp").isNotEmpty()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("Not Found")
                .jsonPath("$.message").isNotEmpty();
    }
}
