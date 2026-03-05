package ru.yandex.practicum.transfer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class TransferServiceApplication {

    public static void main(String[] args) {
        log.info("Starting Transfer Service");
        SpringApplication.run(TransferServiceApplication.class, args);
        log.info("Transfer Service started successfully");
    }
}
