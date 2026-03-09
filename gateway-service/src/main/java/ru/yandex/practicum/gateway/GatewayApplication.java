package ru.yandex.practicum.gateway;

import org.springframework.boot.SpringApplication;
import reactor.core.publisher.Hooks;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(GatewayApplication.class, args);
    }
}
