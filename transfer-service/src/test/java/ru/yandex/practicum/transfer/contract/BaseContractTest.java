package ru.yandex.practicum.transfer.contract;

import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.transfer.config.SecurityConfig;
import ru.yandex.practicum.transfer.controller.TransferController;
import ru.yandex.practicum.transfer.dto.TransferRequest;
import ru.yandex.practicum.transfer.dto.TransferResponse;
import ru.yandex.practicum.transfer.service.TransferService;
import ru.yandex.practicum.transfer.util.SecurityTestUtils;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
@WebFluxTest(controllers = TransferController.class,
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
    private TransferService transferService;

    @BeforeEach
    public void setup() {
        Authentication auth = SecurityTestUtils.createServiceAuthentication();
        WebTestClient authenticatedClient = webTestClient.mutateWith(
                SecurityMockServerConfigurers.mockAuthentication(auth)
        );
        RestAssuredWebTestClient.webTestClient(authenticatedClient);
        TransferResponse transferResponse = new TransferResponse(
                true,
                "Transfer successful",
                9500,
                10500
        );
        when(transferService.transfer(any(TransferRequest.class)))
                .thenReturn(Mono.just(transferResponse));
    }
}
