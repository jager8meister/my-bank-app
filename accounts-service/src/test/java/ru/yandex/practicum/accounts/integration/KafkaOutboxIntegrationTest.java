package ru.yandex.practicum.accounts.integration;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.yandex.practicum.accounts.dto.NotificationEvent;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.service.OutboxRelayService;
import ru.yandex.practicum.accounts.util.SecurityTestUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = "notifications",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@DirtiesContext
@DisplayName("Kafka Outbox Integration Tests — accounts-service")
class KafkaOutboxIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass")
            .withInitScript("test-schema.sql");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> String.format(
                "r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(),
                postgres.getFirstMappedPort(),
                postgres.getDatabaseName()
        ));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        // Disable issuer-uri resolution — NimbusReactiveJwtDecoder is lazy, won't call keycloak at startup
        // Tests bypass JWT via SecurityMockServerConfigurers.mockAuthentication()
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private OutboxRelayService outboxRelayService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    @DisplayName("updateAccount should write OutboxEvent to DB and relay publishes ACCOUNT_UPDATED to Kafka")
    void updateAccount_shouldPublishAccountUpdatedEventToKafka() {
        // Arrange: consumer subscribed before the action
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-outbox-group", "false", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        Consumer<String, NotificationEvent> consumer = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new JsonDeserializer<>(NotificationEvent.class, false)
        ).createConsumer();
        embeddedKafkaBroker.consumeFromEmbeddedTopics(consumer, "notifications");

        // Act: call updateAccount via HTTP (saves OutboxEvent to DB)
        Authentication auth = SecurityTestUtils.createUserAuthentication("ivanov");
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(auth))
                .put()
                .uri("/api/accounts/ivanov")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UpdateAccountRequest("Иван Новый", LocalDate.of(1990, 1, 15)))
                .exchange()
                .expectStatus().isOk();

        // Trigger the outbox relay (normally @Scheduled every 5s)
        outboxRelayService.processOutboxEvents();

        // Assert: message arrives in Kafka within 10 seconds
        ConsumerRecords<String, NotificationEvent> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        assertThat(records.count()).isGreaterThanOrEqualTo(1);
        NotificationEvent event = records.iterator().next().value();
        assertThat(event.login()).isEqualTo("ivanov");
        assertThat(event.type()).isEqualTo("ACCOUNT_UPDATED");
        assertThat(event.message()).contains("Иван Новый");
    }
}
