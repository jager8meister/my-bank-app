package ru.yandex.practicum.notifications.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.notifications.dto.NotificationRequestDto;
import ru.yandex.practicum.notifications.dto.NotificationResponseDto;

@Service
@Slf4j
public class NotificationService {

    public Mono<NotificationResponseDto> sendNotification(NotificationRequestDto request) {
        log.info("Sending notification to {} - Type: {}, Message: {}",
                request.recipient(),
                request.type(),
                request.message());
        return Mono.just(new NotificationResponseDto(
                true,
                "Notification sent successfully to " + request.recipient()
        ));
    }
}
