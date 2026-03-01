package ru.yandex.practicum.mybankfront.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class CustomOidcLogoutSuccessHandler implements LogoutSuccessHandler {

    @Value("${spring.security.oauth2.client.provider.keycloak.end-session-endpoint}")
    private String endSessionEndpoint;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException, ServletException {
        log.info("Custom OIDC logout success handler invoked");
        String idToken = (String) request.getAttribute(OidcLogoutHandler.ID_TOKEN_ATTR);
        if (idToken != null) {
            log.info("Retrieved id_token from request attribute (length: {})", idToken.length());
        } else {
            log.warn("No id_token found in request attribute - logout may not work properly!");
        }
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String postLogoutRedirectUri = baseUrl + "/";
        UriComponentsBuilder logoutUrlBuilder = UriComponentsBuilder
                .fromUriString(endSessionEndpoint)
                .queryParam("post_logout_redirect_uri", postLogoutRedirectUri);
        if (idToken != null) {
            logoutUrlBuilder.queryParam("id_token_hint", idToken);
            log.info("Constructed OIDC logout URL with id_token_hint");
        } else {
            log.warn("No id_token available - Keycloak SSO session may not be terminated!");
        }
        String logoutUrl = logoutUrlBuilder.build().toUriString();
        log.info("Redirecting to Keycloak logout: {}", logoutUrl.replace(idToken != null ? idToken : "", "***ID_TOKEN***"));
        request.getSession().invalidate();
        response.sendRedirect(logoutUrl);
    }
}
