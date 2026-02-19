package ru.yandex.practicum.auth.repository;

import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.auth.model.User;

@Repository
public interface UserRepository extends R2dbcRepository<User, Long> {
    Mono<User> findByLogin(String login);
}
