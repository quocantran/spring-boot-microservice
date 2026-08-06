package com.moviebooking.booking.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.booking.service.BookingService;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.event.EventTypes.Topics;
import com.moviebooking.common.event.EventPayloads.*;
import com.moviebooking.common.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingKafkaConsumer {

    private final BookingService bookingService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    private static final List<String> RELEVANT_EVENTS = Arrays.asList(
            Events.SEATS_RESERVED,
            Events.SEAT_RESERVATION_FAILED,
            Events.PAYMENT_PROCESSED,
            Events.PAYMENT_FAILED,
            Events.SEATS_COMPENSATED
    );

    @KafkaListener(topics = {Topics.SEAT_EVENTS, Topics.PAYMENT_EVENTS}, groupId = "booking-service-group")
    public void handleEvents(ConsumerRecord<String, String> record) {
        try {
            String headerEventType = extractHeader(record, "eventType");
            String headerEventId = extractHeader(record, "id");

            String rawValue = record.value();
            if (rawValue == null || rawValue.isEmpty()) {
                return;
            }

            Map<String, Object> mapValue = objectMapper.readValue(rawValue, Map.class);

            String eventType = headerEventType != null ? headerEventType : (String) mapValue.get("eventType");
            String eventId = headerEventId != null ? headerEventId : (String) mapValue.get("id");

            if (eventType == null || eventId == null) {
                log.warn("Missing eventType or eventId in record: {}", record);
                return;
            }

            if (!RELEVANT_EVENTS.contains(eventType)) {
                return;
            }

            Object payloadObj = mapValue.containsKey("payload") ? mapValue.get("payload") : mapValue;
            String payloadJson = objectMapper.writeValueAsString(payloadObj);

            final String targetEventType = eventType;
            final String targetEventId = eventId;

            idempotencyService.processWithIdempotency(targetEventId, targetEventType, () -> {
                try {
                    switch (targetEventType) {
                        case Events.SEATS_RESERVED:
                            SeatsReservedPayload seatsReservedPayload = objectMapper.readValue(payloadJson, SeatsReservedPayload.class);
                            bookingService.handleSeatsReserved(seatsReservedPayload);
                            break;

                        case Events.SEAT_RESERVATION_FAILED:
                            SeatReservationFailedPayload seatFailedPayload = objectMapper.readValue(payloadJson, SeatReservationFailedPayload.class);
                            bookingService.handleSeatReservationFailed(seatFailedPayload);
                            break;

                        case Events.PAYMENT_PROCESSED:
                            PaymentProcessedPayload paymentProcessedPayload = objectMapper.readValue(payloadJson, PaymentProcessedPayload.class);
                            bookingService.handlePaymentProcessed(paymentProcessedPayload);
                            break;

                        case Events.PAYMENT_FAILED:
                            PaymentFailedPayload paymentFailedPayload = objectMapper.readValue(payloadJson, PaymentFailedPayload.class);
                            bookingService.handlePaymentFailed(paymentFailedPayload);
                            break;

                        case Events.SEATS_COMPENSATED:
                            SeatsCompensatedPayload seatsCompensatedPayload = objectMapper.readValue(payloadJson, SeatsCompensatedPayload.class);
                            bookingService.handleSeatsCompensated(seatsCompensatedPayload);
                            break;
                    }
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
