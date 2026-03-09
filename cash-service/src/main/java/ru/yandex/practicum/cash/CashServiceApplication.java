package ru.yandex.practicum.cash;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Hooks;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class CashServiceApplication {

    public static void main(String[] args) {
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(CashServiceApplication.class, args);
        log.info("CashServiceApplication started successfully");
    }
}
