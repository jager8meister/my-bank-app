package ru.yandex.practicum.accounts.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.dto.AccountResponse;
import ru.yandex.practicum.accounts.dto.CreateAccountRequest;
import ru.yandex.practicum.accounts.dto.UpdateAccountRequest;
import ru.yandex.practicum.accounts.service.AccountService;
import ru.yandex.practicum.accounts.util.AuthorizationUtils;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Accounts", description = "Account management operations")
public class AccountController {

    private final AccountService accountService;

    @Operation(
            summary = "Get account information",
            description = "Returns full account information for the authenticated user. Access is restricted to the account owner."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account information returned successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied — not the account owner"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/{login}")
    public Mono<AccountResponse> getAccount(
            @Parameter(description = "Account login (username)", example = "ivanov")
            @PathVariable @NotBlank(message = "Login is required") @Size(max = 20) String login,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkAuthorizationReactive(login, authentication, "You can only access your own account")
                .then(accountService.getAccountInfo(login));
    }

    @Operation(
            summary = "Update account details",
            description = "Updates the name and birthdate of the account. Access is restricted to the account owner."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "403", description = "Access denied — not the account owner"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @PutMapping("/{login}")
    public Mono<AccountResponse> updateAccount(
            @Parameter(description = "Account login (username)", example = "ivanov")
            @PathVariable @NotBlank(message = "Login is required") @Size(max = 20) String login,
            @RequestBody @Valid UpdateAccountRequest request,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkAuthorizationReactive(login, authentication, "You can only access your own account")
                .then(accountService.updateAccount(login, request));
    }

    @Operation(
            summary = "Set account balance directly (internal/microservice)",
            description = "Overwrites the account balance with the given value. Requires microservice-scope. Intended for internal use by other services only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid balance value"),
            @ApiResponse(responseCode = "403", description = "Access denied — microservice scope required"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @PutMapping("/{login}/balance")
    public Mono<Void> updateBalance(
            @Parameter(description = "Account login (username)", example = "ivanov")
            @PathVariable @NotBlank(message = "Login is required") @Size(max = 20) String login,
            @Parameter(description = "New balance value in minor currency units (kopecks)", example = "150000")
            @RequestParam
            @NotNull(message = "Balance is required")
            @Positive(message = "Balance must be positive")
            Long balance,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkMicroserviceAuthorizationReactive(authentication, "Only microservices can update balance directly")
                .then(accountService.updateBalance(login, balance));
    }

    @Operation(
            summary = "Get account balance",
            description = "Returns the current balance of the account in minor currency units (kopecks). Access is restricted to the account owner."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Balance returned successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied — not the account owner"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @GetMapping("/{login}/balance")
    public Mono<Long> getBalance(
            @Parameter(description = "Account login (username)", example = "ivanov")
            @PathVariable @NotBlank(message = "Login is required") @Size(max = 20) String login,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkAuthorizationReactive(login, authentication, "You can only access your own account")
                .then(accountService.getBalance(login));
    }

    @Operation(
            summary = "Deposit cash into account",
            description = "Adds the specified amount to the account balance. Returns the new balance. Access is restricted to the account owner."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Deposit successful, new balance returned"),
            @ApiResponse(responseCode = "400", description = "Invalid or out-of-range amount"),
            @ApiResponse(responseCode = "403", description = "Access denied — not the account owner"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @PostMapping("/{login}/deposit")
    public Mono<Long> depositCash(
            @Parameter(description = "Account login (username)", example = "ivanov")
            @PathVariable @NotBlank(message = "Login is required") @Size(max = 20) String login,
            @Parameter(description = "Amount to deposit in minor currency units (kopecks), max 1 000 000 000", example = "50000")
            @RequestParam
            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            @Max(value = 1_000_000_000L, message = "Amount must not exceed 1000000000")
            Long amount,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkAuthorizationReactive(login, authentication, "You can only access your own account")
                .then(accountService.depositCash(login, amount));
    }

    @Operation(
            summary = "Withdraw cash from account",
            description = "Subtracts the specified amount from the account balance. Returns the new balance. Access is restricted to the account owner."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Withdrawal successful, new balance returned"),
            @ApiResponse(responseCode = "400", description = "Invalid or out-of-range amount, or insufficient funds"),
            @ApiResponse(responseCode = "403", description = "Access denied — not the account owner"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    @PostMapping("/{login}/withdraw")
    public Mono<Long> withdrawCash(
            @Parameter(description = "Account login (username)", example = "ivanov")
            @PathVariable @NotBlank(message = "Login is required") @Size(max = 20) String login,
            @Parameter(description = "Amount to withdraw in minor currency units (kopecks), max 1 000 000 000", example = "20000")
            @RequestParam
            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            @Max(value = 1_000_000_000L, message = "Amount must not exceed 1000000000")
            Long amount,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkAuthorizationReactive(login, authentication, "You can only access your own account")
                .then(accountService.withdrawCash(login, amount));
    }

    @Operation(
            summary = "Register a new account",
            description = "Creates a new bank account for the given login. This endpoint is public and does not require authentication."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body — validation failed"),
            @ApiResponse(responseCode = "409", description = "Account with this login already exists")
    })
    @PostMapping("/register")
    public Mono<ResponseEntity<Void>> createAccount(@RequestBody @Valid CreateAccountRequest request) {
        return accountService.createAccount(request)
                .thenReturn(ResponseEntity.<Void>status(HttpStatus.CREATED).build());
    }

    @Operation(
            summary = "Execute internal money transfer (internal/microservice)",
            description = "Atomically transfers the specified amount from one account to another. " +
                    "Requires microservice-scope. Intended to be called by transfer-service only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer completed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid parameters or insufficient funds"),
            @ApiResponse(responseCode = "403", description = "Access denied — microservice scope required"),
            @ApiResponse(responseCode = "404", description = "Sender or recipient account not found")
    })
    @PostMapping("/internal/transfer")
    public Mono<AccountService.TransferResult> internalTransfer(
            @Parameter(description = "Login of the sender account", example = "ivanov")
            @RequestParam @NotBlank(message = "Sender login is required") @Size(max = 20) String from,
            @Parameter(description = "Login of the recipient account", example = "petrov")
            @RequestParam @NotBlank(message = "Recipient login is required") @Size(max = 20) String to,
            @Parameter(description = "Amount to transfer in minor currency units (kopecks), max 1 000 000 000", example = "10000")
            @RequestParam
            @NotNull(message = "Amount is required")
            @Positive(message = "Amount must be positive")
            @Max(value = 1_000_000_000L, message = "Amount must not exceed 1000000000")
            Long amount,
            Authentication authentication
    ) {
        return AuthorizationUtils.checkMicroserviceAuthorizationReactive(authentication,
                        "Only microservices can call this endpoint")
                .then(accountService.transferMoney(from, to, amount));
    }
}
