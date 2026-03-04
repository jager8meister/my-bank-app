package ru.yandex.practicum.cash.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class SecurityTestUtils {

    public static Authentication createUserAuthentication(String username) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", username)
                .claim("preferred_username", username)
                .claim("scope", "openid profile")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    public static Authentication createServiceAuthentication() {
        Jwt jwt = Jwt.withTokenValue("test-service-token")
                .header("alg", "RS256")
                .claim("sub", "microservices-client")
                .claim("scope", "microservice")
                .claim("azp", "microservices-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_microservice-scope")));
    }

    public static Jwt createJwt(Map<String, Object> claims) {
        var builder = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600));
        claims.forEach(builder::claim);
        return builder.build();
    }
}
