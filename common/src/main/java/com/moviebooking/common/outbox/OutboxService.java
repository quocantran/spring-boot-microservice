package com.moviebooking.common.outbox;

import com.moviebooking.common.entity.OutboxEntity;
import jakarta.persistence.EntityManager;
import lombok.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxRepository outboxRepository;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OutboxEventData {
        private String aggregateType;
        private String aggregateId;
        private String eventType;
        private Object payload;
    }

    /** Create outbox event in the current transaction. */
    @Transactional
    public OutboxEntity createEvent(OutboxEventData eventData) {
        OutboxEntity outbox = buildOutboxEntity(eventData);
        return outboxRepository.save(outbox);
    }

    /** Create outbox event using specific EntityManager to participate in caller transaction. */
    public OutboxEntity createEvent(OutboxEventData eventData, EntityManager entityManager) {
        OutboxEntity outbox = buildOutboxEntity(eventData);
        entityManager.persist(outbox);
        return outbox;
    }

    /** Alias for createEvent(eventData, entityManager). */
    public OutboxEntity createEventInTransaction(EntityManager entityManager, OutboxEventData eventData) {
        return createEvent(eventData, entityManager);
    }

    private OutboxEntity buildOutboxEntity(OutboxEventData eventData) {
        Object payloadObj = eventData.getPayload();

        if (payloadObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = new HashMap<>((Map<String, Object>) payloadObj);
            map.putIfAbsent("timestamp", Instant.now().toString());
            payloadObj = map;
        }

        return OutboxEntity.builder()
                .aggregateType(eventData.getAggregateType())
                .aggregateId(eventData.getAggregateId())
                .eventType(eventData.getEventType())
                .payload(payloadObj)
                .build();
    }
}
