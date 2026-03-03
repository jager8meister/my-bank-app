package ru.yandex.practicum.notifications.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Slf4j
@Tag(name = "Notifications", description = "Notification delivery operations for account events")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "Send a notification",
            description = "Delivers a notification to the specified recipient about a bank account event. " +
                    "Requires SCOPE_microservice-scope authority (service-to-service JWT token)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification processed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body — validation failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Forbidden — token does not have the required microservice scope")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('SCOPE_microservice-scope')")
    public Mono<NotificationResponseDto> sendNotification(
            @RequestBody @Valid NotificationRequestDto request
    ) {
        log.info("Received notification request for recipient: {}", request.recipient());
        return notificationService.sendNotification(request);
    }
}
