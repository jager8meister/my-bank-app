package ru.yandex.practicum.mybankfront.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
class CustomOidcLogoutSuccessHandlerTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpSession session;
    private CustomOidcLogoutSuccessHandler logoutSuccessHandler;
    @BeforeEach
    void setUp() {
        logoutSuccessHandler = new CustomOidcLogoutSuccessHandler();
        ReflectionTestUtils.setField(logoutSuccessHandler, "endSessionEndpoint",
                "http://localhost:9090/realms/bank-realm/protocol/openid-connect/logout");
    }
    @Test
    void shouldRedirectToKeycloakLogoutWithIdToken() throws IOException, ServletException {
        String idToken = "test-id-token-12345";
        when(request.getAttribute(OidcLogoutHandler.ID_TOKEN_ATTR)).thenReturn(idToken);
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8081);
        when(request.getSession()).thenReturn(session);
        logoutSuccessHandler.onLogoutSuccess(request, response, authentication);
        verify(session).invalidate();
        verify(response).sendRedirect(contains("post_logout_redirect_uri=http://localhost:8081/"));
        verify(response).sendRedirect(contains("id_token_hint=" + idToken));
    }
    @Test
    void shouldRedirectToKeycloakLogoutWithoutIdToken() throws IOException, ServletException {
        when(request.getAttribute(OidcLogoutHandler.ID_TOKEN_ATTR)).thenReturn(null);
        when(request.getScheme()).thenReturn("http");
        when(request.getServerName()).thenReturn("localhost");
        when(request.getServerPort()).thenReturn(8081);
        when(request.getSession()).thenReturn(session);
        logoutSuccessHandler.onLogoutSuccess(request, response, authentication);
        verify(session).invalidate();
        verify(response).sendRedirect(contains("post_logout_redirect_uri=http://localhost:8081/"));
        verify(response).sendRedirect(argThat(url -> !url.contains("id_token_hint")));
    }
}
