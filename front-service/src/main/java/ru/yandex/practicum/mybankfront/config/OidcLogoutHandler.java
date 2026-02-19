package ru.yandex.practicum.mybankfront.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OidcLogoutHandler implements LogoutHandler {

    public static final String ID_TOKEN_ATTR = "OIDC_ID_TOKEN";

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        log.info("OidcLogoutHandler invoked - extracting id_token");
        if (authentication instanceof OAuth2AuthenticationToken oauth2Token) {
            Object principal = oauth2Token.getPrincipal();
            if (principal instanceof OidcUser oidcUser) {
                String idToken = oidcUser.getIdToken().getTokenValue();
                request.setAttribute(ID_TOKEN_ATTR, idToken);
                log.info("Stored id_token in request attribute (length: {})", idToken.length());
            } else {
                log.warn("Principal is not an OidcUser: {}", principal.getClass().getName());
            }
        } else {
            log.warn("Authentication is not OAuth2AuthenticationToken: {}",
                    authentication != null ? authentication.getClass().getName() : "null");
        }
    }
}
