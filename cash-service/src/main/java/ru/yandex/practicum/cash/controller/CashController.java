package ru.yandex.practicum.cash.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.cash.dto.CashOperationRequest;
import ru.yandex.practicum.cash.dto.CashResponse;
import ru.yandex.practicum.cash.service.CashService;
import ru.yandex.practicum.cash.util.AuthorizationUtils;

@RestController
@RequestMapping("/api/cash")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Cash Operations", description = "Cash deposit and withdrawal operations")
public class CashController {

    private final CashService cashService;

    @Operation(
            summary = "Process a cash operation",
            description = "Performs a cash deposit (PUT) or withdrawal (GET) on the account identified by the given login. " +
                    "The authenticated user must match the login in the path."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cash operation completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body or path variable"),
            @ApiResponse(responseCode = "403", description = "Access denied — authenticated user does not match the requested login"),
            @ApiResponse(responseCode = "401", description = "Unauthorized — missing or invalid JWT token")
    })
    @PostMapping("/{login}")
    public Mono<CashResponse> processCashOperation(
            @Parameter(description = "Account owner login", example = "ivanov")
            @PathVariable @NotBlank(message = "Login is required") String login,
            @RequestBody @Valid CashOperationRequest operation,
            Authentication authentication
    ) {
        log.info("Cash operation request: login={}, action={}, amount={}", login, operation.action(), operation.value());
        return AuthorizationUtils.checkAuthorizationReactive(
                login,
                authentication,
                "You can only perform operations on your own account"
        ).then(cashService.processCashOperation(login, operation));
    }
}
