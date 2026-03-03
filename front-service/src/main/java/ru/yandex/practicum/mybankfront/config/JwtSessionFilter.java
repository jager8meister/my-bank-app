package ru.yandex.practicum.mybankfront.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.mybankfront.dto.TokenResponse;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class JwtSessionFilter extends OncePerRequestFilter {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String authServiceUrl;

    private final HttpSessionSecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/login")
                || path.startsWith("/register")
                || path.startsWith("/actuator")
                || path.startsWith("/logout");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            redirectToLogin(response, request);
            return;
        }

        String accessToken = (String) session.getAttribute("ACCESS_TOKEN");
        if (accessToken == null) {
            redirectToLogin(response, request);
            return;
        }

        Long expiresAt = (Long) session.getAttribute("TOKEN_EXPIRES_AT");
        if (expiresAt != null && System.currentTimeMillis() / 1000 >= expiresAt) {
            String refreshToken = (String) session.getAttribute("REFRESH_TOKEN");
            if (refreshToken == null) {
                session.invalidate();
                redirectToLogin(response, request);
                return;
            }
            try {
                TokenResponse tokenResponse = webClient.post()
                        .uri(authServiceUrl + "/api/auth/refresh?refreshToken=" + refreshToken)
                        .retrieve()
                        .bodyToMono(TokenResponse.class)
                        .block();
                if (tokenResponse == null || tokenResponse.accessToken() == null) {
                    session.invalidate();
                    redirectToLogin(response, request);
                    return;
                }
                accessToken = tokenResponse.accessToken();
                session.setAttribute("ACCESS_TOKEN", accessToken);
                session.setAttribute("REFRESH_TOKEN", tokenResponse.refreshToken());
                session.setAttribute("TOKEN_EXPIRES_AT",
                        System.currentTimeMillis() / 1000 + tokenResponse.expiresIn() - 30);
                log.debug("Token refreshed successfully");
            } catch (Exception e) {
                log.warn("Token refresh failed: {}", e.getMessage());
                session.invalidate();
                redirectToLogin(response, request);
                return;
            }
        }

        try {
            String username = extractUsername(accessToken);
            log.debug("JwtFilter authenticated user: {}", username);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, null, List.of());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, request, response);
        } catch (Exception e) {
            log.warn("Failed to parse JWT: {}", e.getMessage());
            session.invalidate();
            redirectToLogin(response, request);
            return;
        }

        chain.doFilter(request, response);
    }

    private String extractUsername(String token) throws Exception {
        String[] parts = token.split("\\.");
        String payload = parts[1];
        int pad = payload.length() % 4;
        if (pad > 0) payload = payload + "=".repeat(4 - pad);
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        Map<?, ?> claims = objectMapper.readValue(decoded, Map.class);
        return (String) claims.get("preferred_username");
    }

    private void redirectToLogin(HttpServletResponse response, HttpServletRequest request)
            throws IOException {
        response.sendRedirect(request.getContextPath() + "/login");
    }
}
