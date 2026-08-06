package com.moviebooking.common.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "outbox", indexes = {
    @Index(name = "idx_outbox_aggregate", columnList = "aggregate_type, aggregate_id"),
    @Index(name = "idx_outbox_event_type", columnList = "event_type"),
    @Index(name = "idx_outbox_processed", columnList = "processed")
})
public class OutboxEntity {

    @Id
    @Column(length = 36)
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    @Column(name = "aggregate_type", nullable = false, length = 255)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 36)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 255)
    private String eventType;

    @Convert(converter = JsonConverter.class)
    @Column(nullable = false, columnDefinition = "json")
    private Object payload;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private boolean processed = false;

    public OutboxEntity(String aggregateType, String aggregateId, String eventType, Object payload) {
        this.id = UUID.randomUUID().toString();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
        this.processed = false;
    }
}
