package com.moviebooking.booking.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.constants.KafkaConstants;
import com.moviebooking.common.event.EventHandler;
import com.moviebooking.common.event.EventHandlerRegistry;
import com.moviebooking.common.event.EventTypes.Topics;
import com.moviebooking.common.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Kafka consumer for booking-service using Strategy Pattern (EventHandlerRegistry)
 * for event routing instead of switch-case, adhering to Open/Closed Principle.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingKafkaConsumer {

    private final EventHandlerRegistry eventHandlerRegistry;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = { Topics.SEAT_EVENTS, Topics.PAYMENT_EVENTS }, groupId = KafkaConstants.GROUP_BOOKING_SERVICE)
    public void handleEvents(ConsumerRecord<String, String> record) {
        try {
            String headerEventType = extractHeader(record, KafkaConstants.HEADER_EVENT_TYPE);
            String headerEventId = extractHeader(record, KafkaConstants.HEADER_EVENT_ID);

            String rawValue = record.value();
            if (rawValue == null || rawValue.isEmpty()) {
                return;
            }

            Map<String, Object> mapValue = objectMapper.readValue(rawValue, Map.class);

            String eventType = headerEventType != null ? headerEventType
                    : (String) mapValue.get(KafkaConstants.HEADER_EVENT_TYPE);
            String eventId = headerEventId != null ? headerEventId
                    : (String) mapValue.get(KafkaConstants.HEADER_EVENT_ID);

            if (eventType == null || eventId == null) {
                log.warn("Missing eventType or eventId in record: {}", record);
                return;
            }

            Optional<EventHandler<Object>> handlerOpt = eventHandlerRegistry.getHandler(eventType);
            if (handlerOpt.isEmpty()) {
                return;
            }

            EventHandler<Object> handler = handlerOpt.get();
            Object payloadObj = mapValue.containsKey("payload") ? mapValue.get("payload") : mapValue;
            String payloadJson = objectMapper.writeValueAsString(payloadObj);

            final String targetEventType = eventType;
            final String targetEventId = eventId;

            idempotencyService.processWithIdempotency(targetEventId, targetEventType, () -> {
                try {
                    Object typedPayload = objectMapper.readValue(payloadJson, handler.payloadType());
                    handler.handle(typedPayload);
                } catch (Exception e) {
                    log.error("Failed to process eventId: {}, eventType: {}", targetEventId, targetEventType, e);
                    throw new RuntimeException("Error handling event: " + targetEventType, e);
                }
            });

        } catch (Exception e) {
            log.error("Error consuming record from Kafka", e);
        }
    }

    private String extractHeader(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }
}

