package ru.yandex.practicum.auth;

import org.springframework.boot.SpringApplication;
import reactor.core.publisher.Hooks;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
