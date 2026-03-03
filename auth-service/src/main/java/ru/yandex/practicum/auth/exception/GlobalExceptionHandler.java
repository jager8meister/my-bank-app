package ru.yandex.practicum.auth.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Mono<Map<String, String>> handleValidation(WebExchangeBindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return Mono.just(Map.of("error", message));
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Mono<Map<String, String>> handleConflict(RuntimeException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("Логин уже занят")) {
            return Mono.just(Map.of("error", ex.getMessage()));
        }
        return Mono.just(Map.of("error", "Registration failed: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Mono<Map<String, String>> handleGeneral(Exception ex) {
        return Mono.just(Map.of("error", "Internal server error"));
    }
}
