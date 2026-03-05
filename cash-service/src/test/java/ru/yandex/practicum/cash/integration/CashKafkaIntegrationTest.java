package ru.yandex.practicum.cash.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.cash.dto.CashAction;
import ru.yandex.practicum.cash.dto.CashOperationRequest;
import ru.yandex.practicum.cash.dto.NotificationEvent;
import ru.yandex.practicum.cash.service.CashService;

import java.time.Duration;
import java.util.Map;
import java.util.stream.StreamSupport;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration"
)
@EmbeddedKafka(partitions = 1, topics = "notifications",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
@DisplayName("Cash Kafka Integration Tests")
class CashKafkaIntegrationTest {

    // Start WireMock statically so it's available for @DynamicPropertySource
    static final WireMockServer wireMockServer =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("services.accounts.host", () -> "localhost");
        registry.add("services.accounts.port", () -> wireMockServer.port());
    }

    /**
     * Provide a plain WebClient (no OAuth2 filter) and mock OAuth2 client stubs so that
     * WebClientConfig can be constructed without a real Keycloak registration.
     */
    @TestConfiguration
    static class TestWebClientConfig {

        @Bean
        @Primary
        public WebClient plainWebClient() {
            return WebClient.builder().build();
        }

        @Bean
        public ReactiveClientRegistrationRepository clientRegistrationRepository() {
            return Mockito.mock(ReactiveClientRegistrationRepository.class);
        }

        @Bean
        public ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
            return Mockito.mock(ServerOAuth2AuthorizedClientRepository.class);
        }
    }

    @MockitoBean
    private ReactiveJwtDecoder reactiveJwtDecoder;

    @Autowired
    private CashService cashService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    private Consumer<String, NotificationEvent> createConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "false", embeddedKafkaBroker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, NotificationEvent> consumer = new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(NotificationEvent.class, false)
        ).createConsumer();
        embeddedKafkaBroker.consumeFromEmbeddedTopics(consumer, "notifications");
        return consumer;
    }

    @Test
    @DisplayName("processCashOperation deposit should publish CASH_DEPOSIT event to Kafka")
    void deposit_shouldPublishCashDepositEvent() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/accounts/ivanov/deposit"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("5500")));

        Consumer<String, NotificationEvent> consumer = createConsumer("cash-deposit-group");

        cashService.processCashOperation("ivanov", new CashOperationRequest(500L, CashAction.PUT)).block();

        ConsumerRecords<String, NotificationEvent> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        NotificationEvent event = StreamSupport.stream(records.spliterator(), false)
                .map(r -> r.value())
                .filter(e -> "CASH_DEPOSIT".equals(e.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No CASH_DEPOSIT event found"));
        assertThat(event.login()).isEqualTo("ivanov");
        assertThat(event.message()).contains("500");
    }

    @Test
    @DisplayName("processCashOperation withdrawal should publish CASH_WITHDRAWAL event to Kafka")
    void withdrawal_shouldPublishCashWithdrawalEvent() {
        wireMockServer.stubFor(post(urlPathEqualTo("/api/accounts/ivanov/withdraw"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("4500")));

        Consumer<String, NotificationEvent> consumer = createConsumer("cash-withdrawal-group");

        cashService.processCashOperation("ivanov", new CashOperationRequest(500L, CashAction.GET)).block();

        ConsumerRecords<String, NotificationEvent> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        NotificationEvent event = StreamSupport.stream(records.spliterator(), false)
                .map(r -> r.value())
                .filter(e -> "CASH_WITHDRAWAL".equals(e.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No CASH_WITHDRAWAL event found"));
        assertThat(event.login()).isEqualTo("ivanov");
        assertThat(event.message()).contains("500");
    }
}
