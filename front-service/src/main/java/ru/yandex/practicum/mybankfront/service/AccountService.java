package ru.yandex.practicum.mybankfront.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mybankfront.dto.CashAction;
import ru.yandex.practicum.mybankfront.store.NotificationStore;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final WebClient webClient;
    private final NotificationStore notificationStore;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    public Mono<Map<String, Object>> getAccountInfo(String login, String accessToken) {
        log.info("Fetching account info for user: {}", login);
        return webClient.get()
                .uri(gatewayUrl + "/api/accounts/" + login)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> (Map<String, Object>) map)
                .onErrorResume(e -> {
                    log.error("Failed to get account info for {}: {}", login, e.getMessage());
                    return Mono.just(createErrorResponse(extractErrorMessage(e)));
                });
    }

    public Mono<Map<String, Object>> updateAccount(String login, String name, LocalDate birthdate, String accessToken) {
        log.info("Updating account profile for user: {}", login);
        Map<String, Object> request = Map.of("name", name, "birthdate", birthdate);
        return webClient.put()
                .uri(gatewayUrl + "/api/accounts/" + login)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> (Map<String, Object>) map)
                .onErrorResume(e -> {
                    log.error("Failed to update account for {}: {}", login, e.getMessage());
                    return getAccountInfo(login, accessToken)
                            .map(accountInfo -> {
                                accountInfo.put("errors", List.of(extractErrorMessage(e)));
                                return accountInfo;
                            })
                            .onErrorResume(fallback -> {
                                log.error("Failed to reload account info after update error for {}: {}", login, fallback.getMessage());
                                return Mono.just(createErrorResponse(extractErrorMessage(e)));
                            });
                });
    }

    public Mono<Map<String, Object>> processCash(String login, long value, CashAction action, String accessToken) {
        log.info("Processing cash action={} amount={} for user: {}", action, value, login);
        Map<String, Object> request = Map.of("value", value, "action", action.toString());
        return webClient.post()
                .uri(gatewayUrl + "/api/cash/" + login)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> (Map<String, Object>) map)
                .flatMap(cashResponse -> {
                    if (cashResponse.get("errors") != null) {
                        return getAccountInfo(login, accessToken)
                                .map(accountInfo -> {
                                    accountInfo.put("errors", cashResponse.get("errors"));
                                    return accountInfo;
                                });
                    }
                    return getAccountInfo(login, accessToken)
                            .flatMap(accountInfo -> Mono.delay(Duration.ofMillis(500))
                                    .map(__ -> {
                                        String notification = notificationStore.pop(login);
                                        if (notification != null) accountInfo.put("info", notification);
                                        return accountInfo;
                                    }));
                })
                .onErrorResume(e -> {
                    log.error("Failed to process cash {} for {}: {}", action, login, e.getMessage());
                    return getAccountInfo(login, accessToken)
                            .map(accountInfo -> {
                                accountInfo.put("errors", List.of(extractErrorMessage(e)));
                                return accountInfo;
                            })
                            .onErrorResume(fallback -> {
                                log.error("Failed to reload account info after cash error for {}: {}", login, fallback.getMessage());
                                return Mono.just(createErrorResponse(extractErrorMessage(e)));
                            });
                });
    }

    public Mono<Map<String, Object>> transfer(String login, long value, String toLogin, String accessToken) {
        log.info("Initiating transfer amount={} from user: {} to: {}", value, login, toLogin);
        Map<String, Object> request = Map.of("senderLogin", login, "recipientLogin", toLogin, "amount", value);
        return webClient.post()
                .uri(gatewayUrl + "/api/transfer")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .map(map -> (Map<String, Object>) map)
                .flatMap(transferResponse -> {
                    Boolean success = (Boolean) transferResponse.get("success");
                    String message = (String) transferResponse.get("message");
                    return getAccountInfo(login, accessToken)
                            .flatMap(accountInfo -> {
                                if (success != null && success) {
                                    return Mono.delay(Duration.ofMillis(500))
                                            .map(__ -> {
                                                String notification = notificationStore.pop(login);
                                                if (notification != null) accountInfo.put("info", notification);
                                                return accountInfo;
                                            });
                                } else {
                                    accountInfo.put("errors", List.of(message));
                                    return Mono.just(accountInfo);
                                }
                            });
                })
                .onErrorResume(e -> {
                    log.error("Failed to process transfer from {} to {}: {}", login, toLogin, e.getMessage());
                    return getAccountInfo(login, accessToken)
                            .map(accountInfo -> {
                                accountInfo.put("errors", List.of(extractErrorMessage(e)));
                                return accountInfo;
                            })
                            .onErrorResume(fallbackError -> {
                                log.error("Failed to reload account info after transfer error for {}: {}", login, fallbackError.getMessage());
                                return Mono.just(createErrorResponse(extractErrorMessage(e)));
                            });
                });
    }

    private String extractErrorMessage(Throwable e) {
        if (e instanceof WebClientResponseException webEx) {
            try {
                String responseBody = webEx.getResponseBodyAsString();
                Map<String, Object> errorResponse = objectMapper.readValue(responseBody, Map.class);
                String message = (String) errorResponse.get("message");
                if (message != null) {
                    if (message.toLowerCase().contains("insufficient funds")) {
                        return "Недостаточно средств на счёте";
                    }
                    if (message.toLowerCase().contains("cannot transfer to yourself")) {
                        return "Нельзя переводить средства самому себе";
                    }
                    if (message.toLowerCase().contains("amount must be positive")) {
                        return "Сумма перевода должна быть больше нуля";
                    }
                    if (message.toLowerCase().contains("not found")) {
                        return "Получатель не найден";
                    }
                    return message;
                }
            } catch (Exception ex) {
            }
        }
        return "Ошибка: " + e.getMessage();
    }

    private Map<String, Object> createErrorResponse(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("name", "Error");
        response.put("birthdate", LocalDate.now().format(DateTimeFormatter.ISO_DATE));
        response.put("sum", 0);
        response.put("accounts", List.of());
        response.put("errors", List.of(errorMessage));
        response.put("info", null);
        return response;
    }
}
