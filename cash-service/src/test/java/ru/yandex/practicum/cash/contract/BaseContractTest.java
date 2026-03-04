package ru.yandex.practicum.cash.contract;

import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.cash.controller.CashController;
import ru.yandex.practicum.cash.dto.CashAction;
import ru.yandex.practicum.cash.dto.CashOperationRequest;
import ru.yandex.practicum.cash.dto.CashResponse;
import ru.yandex.practicum.cash.service.CashService;
import ru.yandex.practicum.cash.util.SecurityTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
@AutoConfigureWebTestClient(timeout = "36000")
public abstract class BaseContractTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CashService cashService;

    @BeforeEach
    public void setup() {
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        WebTestClient authenticatedClient = webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockAuthentication(auth)
        );
        RestAssuredWebTestClient.webTestClient(authenticatedClient);
        CashResponse putResponse = new CashResponse(
                11000L,
                null,
                "Deposit successful"
        );
        when(cashService.processCashOperation(anyString(), any(CashOperationRequest.class)))
                .thenAnswer(invocation -> {
                    CashOperationRequest request = invocation.getArgument(1);
                    if (request.action() == CashAction.PUT) {
                        return Mono.just(putResponse);
                    } else {
                        CashResponse getResponse = new CashResponse(
                                9000L,
                                null,
                                "Withdrawal successful"
                        );
                        return Mono.just(getResponse);
                    }
                });
    }
}
