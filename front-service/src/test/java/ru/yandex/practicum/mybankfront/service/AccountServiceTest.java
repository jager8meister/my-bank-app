package ru.yandex.practicum.mybankfront.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mybankfront.client.NotificationsClient;
import ru.yandex.practicum.mybankfront.dto.CashAction;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private WebClient webClient;

    @Mock
    private NotificationsClient notificationsClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(webClient, notificationsClient);
        ReflectionTestUtils.setField(accountService, "gatewayUrl", "http://localhost:8080");
        lenient().when(notificationsClient.getPendingNotification(anyString())).thenReturn(Mono.empty());
    }

    @Test
    void shouldGetAccountInfoSuccessfully() {
        Map<String, Object> accountData = Map.of(
                "name", "Иван Иванов",
                "birthdate", "1990-01-15",
                "sum", 5000,
                "accounts", List.of()
        );
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(accountData));
        Mono<Map<String, Object>> result = accountService.getAccountInfo("ivanov", "test-token");
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.get("name")).isEqualTo("Иван Иванов");
                    assertThat(response.get("sum")).isEqualTo(5000);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleGetAccountInfoError() {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(new RuntimeException("Connection error")));
        Mono<Map<String, Object>> result = accountService.getAccountInfo("ivanov", "test-token");
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.get("name")).isEqualTo("Error");
                    assertThat(response.get("errors")).isNotNull();
                    List<String> errors = (List<String>) response.get("errors");
                    assertThat(errors).anyMatch(e -> e.contains("Connection error"));
                })
                .verifyComplete();
    }

    @Test
    void shouldUpdateAccountSuccessfully() {
        Map<String, Object> updatedData = Map.of(
                "name", "Иван Петрович Иванов",
                "birthdate", "1990-01-15",
                "sum", 5000
        );
        when(webClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(updatedData));
        Mono<Map<String, Object>> result = accountService.updateAccount(
                "ivanov",
                "Иван Петрович Иванов",
                LocalDate.of(1990, 1, 15),
                "test-token"
        );
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.get("name")).isEqualTo("Иван Петрович Иванов");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleUpdateAccountError() {
        when(webClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(new RuntimeException("Update failed")));
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
        Mono<Map<String, Object>> result = accountService.updateAccount(
                "ivanov",
                "New Name",
                LocalDate.now(),
                "test-token"
        );
        StepVerifier.create(result)
                .assertNext(response -> {
                    List<String> errors = (List<String>) response.get("errors");
                    assertThat(errors).anyMatch(e -> e.contains("Update failed"));
                })
                .verifyComplete();
    }

    @Test
    void shouldProcessCashSuccessWithoutInfoOrErrors() {
        Map<String, Object> cashResponse = Map.of(
                "status", "ok",
                "balance", 5500
        );
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class)).thenReturn(Mono.just(cashResponse));
        Map<String, Object> accountData = Map.of(
                "name", "Иван Иванов",
                "birthdate", "1990-01-15",
                "sum", 5500,
                "accounts", List.of()
        );
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.just(cashResponse))
                .thenReturn(Mono.just(accountData));
        Mono<Map<String, Object>> result = accountService.processCash("ivanov", 500, CashAction.PUT, "test-token");
        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response).containsEntry("name", "Иван Иванов");
                    assertThat(response).containsEntry("sum", 5500);
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleProcessCashError() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(new RuntimeException("Cash service error")));
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);
        Mono<Map<String, Object>> result = accountService.processCash("ivanov", 500, CashAction.PUT, "test-token");
        StepVerifier.create(result)
                .assertNext(response -> {
                    List<String> errors = (List<String>) response.get("errors");
                    assertThat(errors).anyMatch(e -> e.contains("Cash service error"));
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleTransferError() {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.headers(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(new RuntimeException("Transfer service down")));

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.headers(any())).thenReturn(requestHeadersSpec);

        Map<String, Object> accountData = Map.of(
                "name", "Иван Иванов",
                "birthdate", "1990-01-15",
                "sum", 5000,
                "accounts", List.of()
        );
        when(responseSpec.bodyToMono(Map.class))
                .thenReturn(Mono.error(new RuntimeException("Transfer service down")))
                .thenReturn(Mono.just(accountData));

        Mono<Map<String, Object>> result = accountService.transfer("ivanov", 100, "petrov", "test-token");
        StepVerifier.create(result)
                .assertNext(response -> {
                    List<String> errors = (List<String>) response.get("errors");
                    assertThat(errors).anyMatch(e -> e.contains("Transfer service down"));
                })
                .verifyComplete();
    }
}
