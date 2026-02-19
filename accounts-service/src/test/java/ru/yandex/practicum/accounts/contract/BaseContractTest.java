package ru.yandex.practicum.accounts.contract;

import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.controller.AccountController;
import ru.yandex.practicum.accounts.dto.AccountResponse;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.service.AccountService;
import ru.yandex.practicum.accounts.util.SecurityTestUtils;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
@WebFluxTest(controllers = AccountController.class,
    properties = {
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false"
    },
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration.class
    }
)
@Import(TestSecurityConfig.class)
@AutoConfigureWebTestClient(timeout = "36000")
public abstract class BaseContractTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private AccountService accountService;

    @BeforeEach
    public void setup() {
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        WebTestClient authenticatedClient = webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockAuthentication(auth)
        );
        RestAssuredWebTestClient.webTestClient(authenticatedClient);
        AccountResponse mockResponse = new AccountResponse(
                "Ivan Ivanov",
                LocalDate.of(1990, 1, 1),
                10000,
                List.of(),
                null,
                null
        );
        when(accountService.getAccountInfo(anyString()))
                .thenReturn(Mono.just(mockResponse));
        when(accountService.updateAccount(anyString(), any(UpdateAccountRequest.class)))
                .thenReturn(Mono.just(mockResponse));
        when(accountService.getBalance(anyString()))
                .thenReturn(Mono.just(10000));
        when(accountService.depositCash(anyString(), anyInt()))
                .thenReturn(Mono.just(11000));
        when(accountService.withdrawCash(anyString(), anyInt()))
                .thenReturn(Mono.just(9000));
        when(accountService.updateBalance(anyString(), anyInt()))
                .thenReturn(Mono.empty());
        AccountService.TransferResult transferResult = new AccountService.TransferResult(
                9500,
                10500,
                "Ivan Ivanov",
                "Petr Petrov"
        );
        when(accountService.transferMoney(anyString(), anyString(), anyInt()))
                .thenReturn(Mono.just(transferResult));
    }
}
