package ru.yandex.practicum.notifications.contract;

import io.restassured.module.webtestclient.RestAssuredWebTestClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.notifications.controller.NotificationController;
import ru.yandex.practicum.notifications.dto.NotificationRequestDto;
import ru.yandex.practicum.notifications.dto.NotificationResponseDto;
import ru.yandex.practicum.notifications.service.NotificationService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = NotificationController.class,
    properties = {"spring.cloud.config.enabled=false"}
)
@Import(TestSecurityConfig.class)
@AutoConfigureWebTestClient(timeout = "36000")
public abstract class BaseContractTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    public void setup() {
        RestAssuredWebTestClient.webTestClient(webTestClient);
        NotificationResponseDto response = new NotificationResponseDto(
                true,
                "Notification sent successfully"
        );
        when(notificationService.sendNotification(any(NotificationRequestDto.class)))
                .thenReturn(Mono.just(response));
    }
}
