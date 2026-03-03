package ru.yandex.practicum.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.auth.dto.AuthRequest;
import ru.yandex.practicum.auth.dto.AuthResponse;
import ru.yandex.practicum.auth.model.User;
import ru.yandex.practicum.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User ivanov;

    @BeforeEach
    void setUp() {
        ivanov = new User(1L, "ivanov", "hashed_password", "USER");
    }

    // -------------------------------------------------------------------------
    // authenticate
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("authenticate — correct credentials → AuthResponse(success=true)")
    void authenticate_correctCredentials_returnsSuccess() {
        AuthRequest request = new AuthRequest("ivanov", "password");
        when(userRepository.findByLogin("ivanov")).thenReturn(Mono.just(ivanov));
        when(passwordEncoder.matches("password", "hashed_password")).thenReturn(true);

        Mono<AuthResponse> result = authService.authenticate(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.authenticated()).isTrue();
                    assertThat(response.login()).isEqualTo("ivanov");
                    assertThat(response.role()).isEqualTo("USER");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("authenticate — wrong password → AuthResponse(success=false)")
    void authenticate_wrongPassword_returnsFailure() {
        AuthRequest request = new AuthRequest("ivanov", "wrongpassword");
        when(userRepository.findByLogin("ivanov")).thenReturn(Mono.just(ivanov));
        when(passwordEncoder.matches("wrongpassword", "hashed_password")).thenReturn(false);

        Mono<AuthResponse> result = authService.authenticate(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.authenticated()).isFalse();
                    assertThat(response.login()).isNull();
                    assertThat(response.role()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("authenticate — user not found → AuthResponse(success=false)")
    void authenticate_userNotFound_returnsFailure() {
        AuthRequest request = new AuthRequest("unknown", "password");
        when(userRepository.findByLogin("unknown")).thenReturn(Mono.empty());

        Mono<AuthResponse> result = authService.authenticate(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertThat(response.authenticated()).isFalse();
                    assertThat(response.login()).isNull();
                    assertThat(response.role()).isNull();
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // validateUser
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("validateUser — user exists → returns user")
    void validateUser_userExists_returnsUser() {
        when(userRepository.findByLogin("ivanov")).thenReturn(Mono.just(ivanov));

        Mono<User> result = authService.validateUser("ivanov");

        StepVerifier.create(result)
                .assertNext(user -> {
                    assertThat(user.getLogin()).isEqualTo("ivanov");
                    assertThat(user.getRole()).isEqualTo("USER");
                    assertThat(user.getId()).isEqualTo(1L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("validateUser — user not found → empty Mono")
    void validateUser_userNotFound_returnsEmpty() {
        when(userRepository.findByLogin("unknown")).thenReturn(Mono.empty());

        Mono<User> result = authService.validateUser("unknown");

        StepVerifier.create(result)
                .verifyComplete();
    }
}
