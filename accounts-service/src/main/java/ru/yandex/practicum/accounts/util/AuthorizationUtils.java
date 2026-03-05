package ru.yandex.practicum.accounts.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.exception.ForbiddenException;
import ru.yandex.practicum.accounts.exception.UnauthorizedException;

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
            log.warn("Authorization check failed: no JWT authentication present for resource owner='{}'", resourceOwner);
            return Mono.error(new UnauthorizedException("Valid JWT authentication required"));
        }
        if (jwtAuth.getAuthorities().stream()
                .anyMatch(a -> "SCOPE_microservice-scope".equals(a.getAuthority()))) {
            log.debug("Authorization granted via microservice scope for resource owner='{}'", resourceOwner);
            return Mono.empty();
        }
        Jwt jwt = jwtAuth.getToken();
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        if (preferredUsername == null || !resourceOwner.equals(preferredUsername)) {
            log.warn("Authorization denied: requester='{}' attempted to access resource owner='{}'",
                    preferredUsername, resourceOwner);
            return Mono.error(new ForbiddenException(errorMessage));
        }
        log.debug("Authorization granted for user='{}' accessing own resource", preferredUsername);
        return Mono.empty();
    }

    public static Mono<Void> checkMicroserviceAuthorizationReactive(
            Authentication authentication,
            String errorMessage
    ) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            log.warn("Microservice authorization check failed: no JWT authentication present");
            return Mono.error(new UnauthorizedException("Valid JWT authentication required"));
        }
        if (jwtAuth.getAuthorities().stream()
                .anyMatch(a -> "SCOPE_microservice-scope".equals(a.getAuthority()))) {
            log.debug("Microservice authorization granted via microservice scope");
            return Mono.empty();
        }
        log.warn("Microservice authorization denied: caller lacks SCOPE_microservice-scope");
        return Mono.error(new ForbiddenException(errorMessage));
    }
}
