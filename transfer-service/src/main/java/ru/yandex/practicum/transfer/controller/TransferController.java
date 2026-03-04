package ru.yandex.practicum.transfer.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Transfers", description = "Money transfer operations between accounts")
public class TransferController {

    private final TransferService transferService;

    @Operation(
            summary = "Execute a money transfer",
            description = "Transfers the specified amount from the sender's account to the recipient's account. " +
                    "The authenticated user must be the sender."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data (e.g., missing fields, non-positive amount)"),
            @ApiResponse(responseCode = "403", description = "Forbidden — authenticated user is not the sender"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT token")
    })
    @PostMapping
    public Mono<TransferResponse> transfer(
            @RequestBody @Valid TransferRequest request,
            Authentication authentication
    ) {
        log.info("Received transfer request from {} to {} amount {}",
                request.senderLogin(), request.recipientLogin(), request.amount());
        return AuthorizationUtils.checkAuthorizationReactive(
                        request.senderLogin(),
                        authentication,
                        "You can only transfer money from your own account")
                .then(transferService.transfer(request));
    }
}
