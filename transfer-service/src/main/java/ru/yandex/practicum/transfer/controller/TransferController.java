package ru.yandex.practicum.transfer.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.transfer.dto.TransferRequest;
import ru.yandex.practicum.transfer.dto.TransferResponse;
import ru.yandex.practicum.transfer.service.TransferService;
import ru.yandex.practicum.transfer.util.AuthorizationUtils;

@RestController
@RequestMapping("/api/transfer")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public Mono<TransferResponse> transfer(
            @RequestBody @Valid TransferRequest request,
            Authentication authentication
    ) {
        AuthorizationUtils.checkAuthorization(
                request.senderLogin(),
                authentication,
                "You can only transfer money from your own account"
        );
        log.info("Received transfer request from {} to {} amount {}",
                request.senderLogin(), request.recipientLogin(), request.amount());
        return transferService.transfer(request);
    }
}
