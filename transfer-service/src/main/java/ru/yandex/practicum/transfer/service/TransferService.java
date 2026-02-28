package ru.yandex.practicum.transfer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.transfer.dto.TransferRequest;
import ru.yandex.practicum.transfer.dto.TransferResponse;
import ru.yandex.practicum.transfer.exception.InsufficientFundsException;
import ru.yandex.practicum.transfer.exception.InvalidTransferException;
import ru.yandex.practicum.transfer.exception.TransferException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${services.accounts.host:accounts-service}")
    private String accountsServiceHost;

    public Mono<TransferResponse> transfer(TransferRequest request) {
        log.info("Processing transfer from {} to {} amount {}",
                request.senderLogin(), request.recipientLogin(), request.amount());
        if (request.amount() == null || request.amount() <= 0) {
            return Mono.error(new InvalidTransferException("Amount must be positive"));
        }
        if (request.senderLogin().equals(request.recipientLogin())) {
            return Mono.error(new InvalidTransferException("Cannot transfer to yourself"));
        }
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("lb")
                        .host(accountsServiceHost)
                        .path("/api/accounts/internal/transfer")
                        .queryParam("from", request.senderLogin())
                        .queryParam("to", request.recipientLogin())
                        .queryParam("amount", request.amount())
                        .build())
                .retrieve()
                .bodyToMono(TransferResult.class)
                .map(result -> {
                    log.info("Transfer successful. Sender new balance: {}, Recipient new balance: {}",
                            result.senderBalance(), result.recipientBalance());
                    return new TransferResponse(
                            true,
                            "Transfer successful",
                            result.senderBalance(),
                            result.recipientBalance()
                    );
                })
                .onErrorResume(e -> {
                    log.error("Transfer failed: {}", e.getMessage());
                    String errorMessage = extractErrorMessage(e);
                    if (errorMessage != null && errorMessage.toLowerCase().contains("insufficient funds")) {
                        return Mono.error(new InsufficientFundsException(errorMessage));
                    }
                    return Mono.error(new TransferException(errorMessage));
                });
    }

    private String extractErrorMessage(Throwable e) {
        if (e instanceof WebClientResponseException webEx) {
            try {
                String responseBody = webEx.getResponseBodyAsString();
                Map<String, Object> errorResponse = objectMapper.readValue(responseBody, Map.class);
                String message = (String) errorResponse.get("message");
                if (message != null) {
                    return message;
                }
            } catch (Exception ex) {
                log.warn("Failed to parse error response: {}", ex.getMessage());
            }
        }
        return "Transfer failed: " + e.getMessage();
    }

    record TransferResult(Long senderBalance, Long recipientBalance, String senderName, String recipientName) {}
}
