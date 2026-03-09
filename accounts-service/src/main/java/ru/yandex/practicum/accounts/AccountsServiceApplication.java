package ru.yandex.practicum.accounts;

import org.springframework.boot.SpringApplication;
import reactor.core.publisher.Hooks;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AccountsServiceApplication {

    public static void main(String[] args) {
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(AccountsServiceApplication.class, args);
    }
}
