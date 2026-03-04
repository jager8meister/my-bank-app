package ru.yandex.practicum.accounts.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "outbox_events", schema = "accounts_schema")
public class OutboxEvent {

    @Id
    private Long id;

    private String eventType;

    private String recipient;

    private String message;

    private LocalDateTime createdAt;

    private boolean processed;

    private LocalDateTime processedAt;
}
