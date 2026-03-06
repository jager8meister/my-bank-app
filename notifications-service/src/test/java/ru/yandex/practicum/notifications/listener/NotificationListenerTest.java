package ru.yandex.practicum.notifications.listener;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.extension.ExtendWith;
import ru.yandex.practicum.notifications.dto.NotificationEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "notifications",
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
@DisplayName("NotificationListener Integration Tests")
class NotificationListenerTest {

    @TestConfiguration
    static class TestKafkaConfig {

        @Bean
        @Primary
        public ConsumerFactory<String, NotificationEvent> consumerFactory(EmbeddedKafkaBroker broker) {
            JsonDeserializer<NotificationEvent> deserializer = new JsonDeserializer<>(NotificationEvent.class, false);
            Map<String, Object> config = new HashMap<>();
            config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            config.put(ConsumerConfig.GROUP_ID_CONFIG, "notifications-group");
            return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
        }

        @Bean
        @Primary
        public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> kafkaListenerContainerFactory(
                ConsumerFactory<String, NotificationEvent> consumerFactory) {
            ConcurrentKafkaListenerContainerFactory<String, NotificationEvent> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);
            return factory;
        }

        @Bean
        public ProducerFactory<String, NotificationEvent> testProducerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> config = new HashMap<>();
            config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
            return new DefaultKafkaProducerFactory<>(config);
        }

        @Bean
        public KafkaTemplate<String, NotificationEvent> testKafkaTemplate(
                ProducerFactory<String, NotificationEvent> testProducerFactory) {
            return new KafkaTemplate<>(testProducerFactory);
        }
    }

    @Autowired
    private KafkaTemplate<String, NotificationEvent> testKafkaTemplate;

    @Test
    @DisplayName("Should receive CASH_DEPOSIT event from Kafka topic and log it")
    void shouldReceiveCashDepositEvent(CapturedOutput output) throws InterruptedException {
        NotificationEvent event = new NotificationEvent(
                "ivanov", "CASH_DEPOSIT", "Положено 500 руб", "2026-03-04T10:00:00");

        testKafkaTemplate.send("notifications", "ivanov", event);
        testKafkaTemplate.flush();
        TimeUnit.SECONDS.sleep(3);

        assertThat(output.getOut())
                .contains("CASH_DEPOSIT")
                .contains("ivanov")
                .contains("500");
    }

    @Test
    @DisplayName("Should receive CASH_WITHDRAWAL event from Kafka topic and log it")
    void shouldReceiveCashWithdrawalEvent(CapturedOutput output) throws InterruptedException {
        NotificationEvent event = new NotificationEvent(
                "ivanov", "CASH_WITHDRAWAL", "Снято 200 руб", "2026-03-04T10:01:00");

        testKafkaTemplate.send("notifications", "ivanov", event);
        testKafkaTemplate.flush();
        TimeUnit.SECONDS.sleep(3);

        assertThat(output.getOut())
                .contains("CASH_WITHDRAWAL")
                .contains("ivanov");
    }

    @Test
    @DisplayName("Should receive TRANSFER_SENT event from Kafka topic and log it")
    void shouldReceiveTransferSentEvent(CapturedOutput output) throws InterruptedException {
        NotificationEvent event = new NotificationEvent(
                "ivanov", "TRANSFER_SENT",
                "Вы перевели 300 руб пользователю petrov. Новый баланс: 4700 руб",
                "2026-03-04T10:02:00");

        testKafkaTemplate.send("notifications", "ivanov", event);
        testKafkaTemplate.flush();
        TimeUnit.SECONDS.sleep(3);

        assertThat(output.getOut())
                .contains("TRANSFER_SENT")
                .contains("ivanov")
                .contains("petrov");
    }

    @Test
    @DisplayName("Should receive ACCOUNT_UPDATED event from Kafka topic and log it")
    void shouldReceiveAccountUpdatedEvent(CapturedOutput output) throws InterruptedException {
        NotificationEvent event = new NotificationEvent(
                "ivanov", "ACCOUNT_UPDATED",
                "Данные вашего аккаунта были обновлены: Иванов Иван",
                "2026-03-04T10:03:00");

        testKafkaTemplate.send("notifications", "ivanov", event);
        testKafkaTemplate.flush();
        TimeUnit.SECONDS.sleep(3);

        assertThat(output.getOut())
                .contains("ACCOUNT_UPDATED")
                .contains("ivanov");
    }
}
