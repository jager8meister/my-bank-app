package ru.yandex.practicum.accounts.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(UnauthorizedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleUnauthorizedException(UnauthorizedException ex) {
        log.error("Unauthorized: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(createErrorResponse(ex.getMessage(), HttpStatus.UNAUTHORIZED)));
    }

    @ExceptionHandler(ForbiddenException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleForbiddenException(ForbiddenException ex) {
        log.error("Forbidden: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(createErrorResponse(ex.getMessage(), HttpStatus.FORBIDDEN)));
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAccountAlreadyExistsException(AccountAlreadyExistsException ex) {
        log.error("Account already exists: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(createErrorResponse(ex.getMessage(), HttpStatus.CONFLICT)));
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAccountNotFoundException(AccountNotFoundException ex) {
        log.error("Account not found: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(createErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND)));
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInsufficientFundsException(InsufficientFundsException ex) {
        log.error("Insufficient funds: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST)));
    }

    @ExceptionHandler(InvalidAmountException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInvalidAmountException(InvalidAmountException ex) {
        log.error("Invalid amount: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST)));
    }

    @ExceptionHandler(InvalidTransferException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleInvalidTransferException(InvalidTransferException ex) {
        log.error("Invalid transfer: {}", ex.getMessage());
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST)));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidationException(WebExchangeBindException ex) {
        log.error("Validation error: {}", ex.getMessage());
        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Validation failed: " + errors, HttpStatus.BAD_REQUEST)));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleConstraintViolationException(ConstraintViolationException ex) {
        log.error("Constraint violation: {}", ex.getMessage());
        String errors = ex.getConstraintViolations()
                .stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining(", "));
        return Mono.just(ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(createErrorResponse("Validation failed: " + errors, HttpStatus.BAD_REQUEST)));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericException(Exception ex) {
        log.error("Unexpected error: ", ex);
        String errorMessage = "Internal server error: " + ex.getMessage();
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(createErrorResponse(errorMessage, HttpStatus.INTERNAL_SERVER_ERROR)));
    }

    private Map<String, Object> createErrorResponse(String message, HttpStatus status) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", status.getReasonPhrase());
        response.put("message", message);
        response.put("status", status.value());
        response.put("timestamp", System.currentTimeMillis());
        return response;
    }
}
