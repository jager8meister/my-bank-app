package ru.yandex.practicum.notifications.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.notifications.dto.NotificationRequestDto;
import ru.yandex.practicum.notifications.dto.NotificationResponseDto;
import ru.yandex.practicum.notifications.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Validated
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_microservice-scope')")
    public Mono<NotificationResponseDto> sendNotification(
            @RequestBody @Valid NotificationRequestDto request
    ) {
        log.info("Received notification request for recipient: {}", request.recipient());
        return notificationService.sendNotification(request);
    }
}
