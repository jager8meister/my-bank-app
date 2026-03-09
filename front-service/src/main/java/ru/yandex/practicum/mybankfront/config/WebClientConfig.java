package ru.yandex.practicum.mybankfront.config;

import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(ObservationRegistry observationRegistry) {
        log.info("Initializing default WebClient bean");
        return WebClient.builder()
                .observationRegistry(observationRegistry)
                .build();
    }

    @Bean
    @Qualifier("plainWebClient")
    public WebClient plainWebClient(ObservationRegistry observationRegistry) {
        log.info("Initializing plainWebClient bean");
        return WebClient.builder()
                .observationRegistry(observationRegistry)
                .build();
    }
}
