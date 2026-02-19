package ru.yandex.practicum.transfer.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.transfer.exception.ForbiddenException;
import ru.yandex.practicum.transfer.exception.UnauthorizedException;

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
        for (var authority : jwtAuth.getAuthorities()) {
            if (authority.getAuthority().contains("microservice")) {
                return Mono.empty();
            }
        }
        Jwt jwt = jwtAuth.getToken();
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername == null || !resourceOwner.equals(preferredUsername)) {
            return Mono.error(new ForbiddenException(errorMessage));
        }
        return Mono.empty();
    }

    public static void checkAuthorization(
            String resourceOwner,
            Authentication authentication,
            String errorMessage
    ) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new UnauthorizedException("Valid JWT authentication required");
        }
        for (var authority : jwtAuth.getAuthorities()) {
            if (authority.getAuthority().contains("microservice")) {
                return;
            }
        }
        Jwt jwt = jwtAuth.getToken();
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername == null || !resourceOwner.equals(preferredUsername)) {
            throw new ForbiddenException(errorMessage);
        }
    }

    public static void checkAuthorization(String resourceOwner, Authentication authentication) {
        checkAuthorization(resourceOwner, authentication, "You can only access your own resources");
    }
}
