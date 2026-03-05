package ru.yandex.practicum.transfer.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
import ru.yandex.practicum.transfer.dto.NotificationEvent;
import ru.yandex.practicum.transfer.dto.TransferRequest;
import ru.yandex.practicum.transfer.service.TransferService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
@DisplayName("Transfer Kafka Integration Tests")
class TransferKafkaIntegrationTest {

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
    private TransferService transferService;

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
    @DisplayName("transfer should publish TRANSFER_SENT and TRANSFER_RECEIVED events to Kafka")
    void transfer_shouldPublishBothTransferEvents() throws Exception {
        // Stub accounts-service internal transfer endpoint
        String transferResult = new ObjectMapper().writeValueAsString(
                Map.of("senderBalance", 4500, "recipientBalance", 3500,
                        "senderName", "Иван Иванов", "recipientName", "Петр Петров"));
        wireMockServer.stubFor(post(urlPathEqualTo("/api/accounts/internal/transfer"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(transferResult)));

        Consumer<String, NotificationEvent> consumer = createConsumer("transfer-events-group");

        transferService.transfer(new TransferRequest("ivanov", "petrov", 500L)).block();

        // Poll until we get 2 messages or timeout
        List<NotificationEvent> events = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 10_000;
        while (events.size() < 2 && System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, NotificationEvent> records =
                    KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2));
            for (ConsumerRecord<String, NotificationEvent> record : records) {
                events.add(record.value());
            }
        }
        consumer.close();

        assertThat(events).hasSize(2);

        NotificationEvent sentEvent = events.stream()
                .filter(e -> "TRANSFER_SENT".equals(e.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("TRANSFER_SENT event not found"));
        assertThat(sentEvent.login()).isEqualTo("ivanov");
        assertThat(sentEvent.message()).contains("500").contains("petrov");

        NotificationEvent receivedEvent = events.stream()
                .filter(e -> "TRANSFER_RECEIVED".equals(e.type()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("TRANSFER_RECEIVED event not found"));
        assertThat(receivedEvent.login()).isEqualTo("petrov");
        assertThat(receivedEvent.message()).contains("500").contains("ivanov");
    }
}
