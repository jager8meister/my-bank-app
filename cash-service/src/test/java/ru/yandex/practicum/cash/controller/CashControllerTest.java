package ru.yandex.practicum.cash.controller;

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
import ru.yandex.practicum.cash.config.TestSecurityConfig;
import ru.yandex.practicum.cash.dto.CashAction;
import ru.yandex.practicum.cash.dto.CashOperationRequest;
import ru.yandex.practicum.cash.dto.CashResponse;
import ru.yandex.practicum.cash.service.CashService;
import ru.yandex.practicum.cash.util.SecurityTestUtils;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
@WebFluxTest(controllers = CashController.class,
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
    },
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration.class
    }
)
@Import(TestSecurityConfig.class)
@DisplayName("CashController WebFlux Tests")
class CashControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private CashService cashService;
    @Test
    @DisplayName("POST /api/cash/{login} - Should process PUT operation")
    void shouldProcessPutOperation() {
        CashOperationRequest request = new CashOperationRequest(500, CashAction.PUT);
        CashResponse response = new CashResponse(5500, null, "Положено 500 руб");
        when(cashService.processCashOperation(eq("ivanov"), any(CashOperationRequest.class)))
                .thenReturn(Mono.just(response));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.newBalance").isEqualTo(5500)
                .jsonPath("$.info").isEqualTo("Положено 500 руб")
                .jsonPath("$.errors").isEmpty();
    }
    @Test
    @DisplayName("POST /api/cash/{login} - Should process GET operation")
    void shouldProcessGetOperation() {
        CashOperationRequest request = new CashOperationRequest(500, CashAction.GET);
        CashResponse response = new CashResponse(4500, null, "Снято 500 руб");
        when(cashService.processCashOperation(eq("ivanov"), any(CashOperationRequest.class)))
                .thenReturn(Mono.just(response));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.newBalance").isEqualTo(4500)
                .jsonPath("$.info").isEqualTo("Снято 500 руб");
    }
    @Test
    @DisplayName("POST /api/cash/{login} - Should return error for insufficient funds")
    void shouldReturnErrorForInsufficientFunds() {
        CashOperationRequest request = new CashOperationRequest(10000, CashAction.GET);
        CashResponse response = new CashResponse(null, List.of("Недостаточно средств на счету"), null);
        when(cashService.processCashOperation(eq("ivanov"), any(CashOperationRequest.class)))
                .thenReturn(Mono.just(response));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.newBalance").isEmpty()
                .jsonPath("$.errors[0]").isEqualTo("Недостаточно средств на счету")
                .jsonPath("$.info").isEmpty();
    }
    @Test
    @DisplayName("POST /api/cash/{login} - Should handle different users")
    void shouldHandleDifferentUsers() {
        CashOperationRequest request = new CashOperationRequest(1000, CashAction.PUT);
        CashResponse response = new CashResponse(4000, null, "Положено 1000 руб");
        when(cashService.processCashOperation(eq("petrov"), any(CashOperationRequest.class)))
                .thenReturn(Mono.just(response));
        Authentication auth = SecurityTestUtils.createUserAuthentication("petrov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/cash/petrov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.newBalance").isEqualTo(4000);
    }
    @Test
    @DisplayName("POST /api/cash/{login} - Should handle service errors")
    void shouldHandleServiceErrors() {
        CashOperationRequest request = new CashOperationRequest(500, CashAction.PUT);
        CashResponse response = new CashResponse(null, List.of("Ошибка операции: Service unavailable"), null);
        when(cashService.processCashOperation(eq("ivanov"), any(CashOperationRequest.class)))
                .thenReturn(Mono.just(response));
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .post()
                .uri("/api/cash/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("Ошибка операции"));
    }
}
