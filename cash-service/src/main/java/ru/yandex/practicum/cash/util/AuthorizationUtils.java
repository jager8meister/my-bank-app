package ru.yandex.practicum.cash.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.cash.exception.ForbiddenException;
import ru.yandex.practicum.cash.exception.UnauthorizedException;

@Slf4j
public final class AuthorizationUtils {

    private AuthorizationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static Mono<Void> checkAuthorizationReactive(
            String resourceOwner,
            Authentication authentication,
            String errorMessage
    ) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            log.warn("Authorization failed: authentication is not a JwtAuthenticationToken, type={}", authentication == null ? "null" : authentication.getClass().getSimpleName());
            return Mono.error(new UnauthorizedException("Valid JWT authentication required"));
        }
        if (jwtAuth.getAuthorities().stream()
                .anyMatch(a -> "SCOPE_microservice-scope".equals(a.getAuthority()))) {
            log.debug("Authorization granted via microservice scope for resource owner={}", resourceOwner);
            return Mono.empty();
        }
        String preferredUsername = jwtAuth.getToken().getClaimAsString("preferred_username");
        if (preferredUsername == null || !resourceOwner.equals(preferredUsername)) {
            log.warn("Authorization denied: authenticated user={} does not match resource owner={}", preferredUsername, resourceOwner);
            return Mono.error(new ForbiddenException(errorMessage));
        }
        log.debug("Authorization granted: user={} matches resource owner={}", preferredUsername, resourceOwner);
        return Mono.empty();
    }
}
