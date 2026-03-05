package ru.yandex.practicum.mybankfront.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient() {
        log.info("Initializing default WebClient bean");
        return WebClient.create();
    }

    @Bean
    @Qualifier("plainWebClient")
    public WebClient plainWebClient() {
        log.info("Initializing plainWebClient bean");
        return WebClient.create();
    }
}
