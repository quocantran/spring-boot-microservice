package com.moviebooking.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.constants.KafkaConstants;
import com.moviebooking.common.event.EventPayloads.SeatsReservedPayload;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.event.EventTypes.Topics;
import com.moviebooking.common.idempotency.IdempotencyService;
import com.moviebooking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentKafkaConsumer {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = Topics.SEAT_EVENTS, groupId = KafkaConstants.GROUP_PAYMENT_SERVICE)
    public void handleSeatEvents(ConsumerRecord<String, String> record) {
        try {
            String headerEventType = extractHeader(record, KafkaConstants.HEADER_EVENT_TYPE);
            String headerEventId = extractHeader(record, KafkaConstants.HEADER_EVENT_ID);

            String rawValue = record.value();
            if (rawValue == null || rawValue.isEmpty()) return;

            Map<String, Object> mapValue = objectMapper.readValue(rawValue, Map.class);

            String eventType = headerEventType != null ? headerEventType : (String) mapValue.get(KafkaConstants.HEADER_EVENT_TYPE);
            String eventId = headerEventId != null ? headerEventId : (String) mapValue.get(KafkaConstants.HEADER_EVENT_ID);

            if (eventType == null || eventId == null) return;

            // Only process SEATS_RESERVED events
            if (!Events.SEATS_RESERVED.equals(eventType)) return;

            Object payloadObj = mapValue.containsKey("payload") ? mapValue.get("payload") : mapValue;
            String payloadJson = objectMapper.writeValueAsString(payloadObj);

            idempotencyService.processWithIdempotency(eventId, eventType, () -> {
                try {
                    SeatsReservedPayload payload = objectMapper.readValue(payloadJson, SeatsReservedPayload.class);
                    paymentService.processPayment(payload);
                } catch (Exception e) {
                    log.error("Failed to process eventId: {}, eventType: {}", eventId, eventType, e);
                    throw new RuntimeException("Error handling event: " + eventType, e);
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
