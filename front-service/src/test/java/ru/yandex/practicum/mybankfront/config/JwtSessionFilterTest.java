package ru.yandex.practicum.mybankfront.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mybankfront.dto.TokenResponse;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtSessionFilterTest {

    private WebClient webClient;
    private JwtSessionFilter filter;

    @BeforeEach
    void setUp() {
        webClient = mock(WebClient.class);
        filter = new JwtSessionFilter(webClient, new ObjectMapper(), "http://gateway-service:8080");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private String createFakeJwt(String username) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"preferred_username\":\"" + username + "\"}").getBytes());
        return header + "." + payload + ".fake-signature";
    }

    @Test
    void shouldNotFilter_loginPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/login");
        assertTrue(filter.shouldNotFilter(request));
    }

    @Test
    void noSession_redirectsToLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/account");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals("/login", response.getRedirectedUrl());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void noToken_redirectsToLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/account");
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals("/login", response.getRedirectedUrl());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void validToken_notExpired_chainsThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/account");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("ACCESS_TOKEN", createFakeJwt("ivanov"));
        session.setAttribute("TOKEN_EXPIRES_AT", Long.MAX_VALUE);
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals("ivanov", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiredToken_refreshSuccess_chainsThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/account");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("ACCESS_TOKEN", createFakeJwt("ivanov"));
        session.setAttribute("REFRESH_TOKEN", "old-refresh-token");
        session.setAttribute("TOKEN_EXPIRES_AT", 0L);
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        String newJwt = createFakeJwt("ivanov");
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TokenResponse.class))
                .thenReturn(Mono.just(new TokenResponse(newJwt, "new-refresh-token", 300)));

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(newJwt, session.getAttribute("ACCESS_TOKEN"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void expiredToken_refreshFails_redirectsToLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/account");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("ACCESS_TOKEN", createFakeJwt("ivanov"));
        session.setAttribute("REFRESH_TOKEN", "expired-refresh-token");
        session.setAttribute("TOKEN_EXPIRES_AT", 0L);
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(TokenResponse.class))
                .thenReturn(Mono.error(new RuntimeException("Refresh failed")));

        filter.doFilterInternal(request, response, chain);

        assertEquals("/login", response.getRedirectedUrl());
        verify(chain, never()).doFilter(any(), any());
    }
}
