package ru.yandex.practicum.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.auth.model.User;
import ru.yandex.practicum.auth.repository.UserRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DataInitializer Unit Tests")
class DataInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TransactionalOperator transactionalOperator;

    @InjectMocks
    private DataInitializer dataInitializer;

    @Test
    @DisplayName("initData — count=0 → creates 3 users (ivanov, petrov, sidorov)")
    void initData_emptyTable_createsUsers() {
        when(userRepository.count()).thenReturn(Mono.just(0L));
        when(passwordEncoder.encode(any())).thenReturn("hashed_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            return Mono.just(new User(1L, u.getLogin(), u.getPassword(), u.getRole()));
        });
        // TransactionalOperator.transactional(publisher) should pass through the publisher
        when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Mono<Void> result = dataInitializer.initData();

        StepVerifier.create(result)
                .verifyComplete();

        verify(userRepository).count();
        verify(passwordEncoder, times(3)).encode("password");
        verify(userRepository, times(3)).save(any(User.class));
    }

    @Test
    @DisplayName("initData — count>0 → skips initialization, no saves")
    void initData_nonEmptyTable_skipsInitialization() {
        when(userRepository.count()).thenReturn(Mono.just(3L));

        Mono<Void> result = dataInitializer.initData();

        StepVerifier.create(result)
                .verifyComplete();

        verify(userRepository).count();
        verify(userRepository, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(any());
        verify(transactionalOperator, never()).transactional(any(Mono.class));
    }
}
