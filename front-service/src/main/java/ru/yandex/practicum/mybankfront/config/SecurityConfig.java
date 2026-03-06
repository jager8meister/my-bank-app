package ru.yandex.practicum.mybankfront.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.client.provider.keycloak.end-session-uri}")
    private String keycloakLogoutUri;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/register", "/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/login")
                        .defaultSuccessUrl("/account", true)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(keycloakLogoutSuccessHandler())
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                )
                .csrf(Customizer.withDefaults());
        return http.build();
    }

    private LogoutSuccessHandler keycloakLogoutSuccessHandler() {
        return (request, response, authentication) -> {
            String user = authentication != null ? authentication.getName() : "unknown";
            log.info("Logout handler invoked for user: {}", user);
            String postLogoutUri = request.getScheme() + "://" + request.getServerName()
                    + ":" + request.getServerPort() + "/login";
            StringBuilder url = new StringBuilder(keycloakLogoutUri)
                    .append("?post_logout_redirect_uri=")
                    .append(URLEncoder.encode(postLogoutUri, StandardCharsets.UTF_8));
            if (authentication instanceof OAuth2AuthenticationToken token
                    && token.getPrincipal() instanceof OidcUser oidcUser) {
                url.append("&id_token_hint=").append(oidcUser.getIdToken().getTokenValue());
            } else {
                url.append("&client_id=front-client");
            }
            response.sendRedirect(url.toString());
        };
    }
}
