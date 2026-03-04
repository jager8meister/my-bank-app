package ru.yandex.practicum.cash.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.cash.exception.ForbiddenException;
import ru.yandex.practicum.cash.exception.UnauthorizedException;

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
            return Mono.error(new UnauthorizedException("Valid JWT authentication required"));
        }
        if (jwtAuth.getAuthorities().stream()
                .anyMatch(a -> "SCOPE_microservice-scope".equals(a.getAuthority()))) {
            return Mono.empty();
        }
        String preferredUsername = jwtAuth.getToken().getClaimAsString("preferred_username");
        if (preferredUsername == null || !resourceOwner.equals(preferredUsername)) {
            return Mono.error(new ForbiddenException(errorMessage));
        }
        return Mono.empty();
    }
}
