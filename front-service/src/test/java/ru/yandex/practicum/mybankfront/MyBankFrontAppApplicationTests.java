package ru.yandex.practicum.mybankfront;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "notifications", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class MyBankFrontAppApplicationTests {

    @Test
    void contextLoads() {
    }
}
