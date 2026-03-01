package ru.yandex.practicum.mybankfront.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import java.time.Instant;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class OidcLogoutHandlerTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private OAuth2AuthenticationToken authentication;

    @Mock
    private OidcUser oidcUser;

    @Mock
    private OidcIdToken idToken;
    private OidcLogoutHandler logoutHandler;
    @BeforeEach
    void setUp() {
        logoutHandler = new OidcLogoutHandler();
    }
    @Test
    void shouldExtractIdTokenFromOidcUser() {
        String tokenValue = "test-id-token-12345";
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getIdToken()).thenReturn(idToken);
        when(idToken.getTokenValue()).thenReturn(tokenValue);
        logoutHandler.logout(request, response, authentication);
        verify(request).setAttribute(OidcLogoutHandler.ID_TOKEN_ATTR, tokenValue);
    }
    @Test
    void shouldHandleNonOAuth2Authentication() {
        Authentication nonOAuth2Auth = mock(Authentication.class);
        logoutHandler.logout(request, response, nonOAuth2Auth);
        verify(request, never()).setAttribute(anyString(), any());
    }
    @Test
    void shouldHandleNullAuthentication() {
        logoutHandler.logout(request, response, null);
        verify(request, never()).setAttribute(anyString(), any());
    }
}
