package ru.yandex.practicum.accounts.repository;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.accounts.model.OutboxEvent;

public interface OutboxEventRepository extends ReactiveCrudRepository<OutboxEvent, Long> {

    Flux<OutboxEvent> findByProcessedFalse();

    @Modifying
    @Query("UPDATE accounts_schema.outbox_events SET processed = true, processed_at = NOW() WHERE id = :id")
    Mono<Integer> markAsProcessed(Long id);

}
