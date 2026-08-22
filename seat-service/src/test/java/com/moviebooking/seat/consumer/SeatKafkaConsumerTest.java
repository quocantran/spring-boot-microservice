package com.moviebooking.seat.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.constants.KafkaConstants;
import com.moviebooking.common.event.EventHandlerRegistry;
import com.moviebooking.common.event.EventPayloads.*;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.idempotency.IdempotencyService;
import com.moviebooking.seat.consumer.handler.BookingCreatedHandler;
import com.moviebooking.seat.consumer.handler.PaymentFailedHandler;
import com.moviebooking.seat.consumer.handler.PaymentProcessedHandler;
import com.moviebooking.seat.service.SeatService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatKafkaConsumerTest {

    @Mock
    private SeatService seatService;

    @Mock
    private IdempotencyService idempotencyService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SeatKafkaConsumer seatKafkaConsumer;

    @BeforeEach
    void setUp() {
        EventHandlerRegistry registry = EventHandlerRegistry.of(List.of(
                new BookingCreatedHandler(seatService),
                new PaymentProcessedHandler(seatService),
                new PaymentFailedHandler(seatService)
        ));
        seatKafkaConsumer = new SeatKafkaConsumer(registry, idempotencyService, objectMapper);
    }

    private ConsumerRecord<String, String> createRecord(String eventType, String eventId, String value) {
        RecordHeaders headers = new RecordHeaders();
        if (eventType != null) {
            headers.add(new RecordHeader(KafkaConstants.HEADER_EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8)));
        }
        if (eventId != null) {
            headers.add(new RecordHeader(KafkaConstants.HEADER_EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8)));
        }
        ConsumerRecord<String, String> record = new ConsumerRecord<>("booking.events", 0, 0L, "key", value);
        headers.forEach(h -> record.headers().add(h));
        return record;
    }

    @Test
    @DisplayName("Should route BOOKING_CREATED via Strategy Pattern")
    void shouldRouteBookingCreated() throws Exception {
        String eventId = "evt-seat-1";
        BookingCreatedPayload payload = BookingCreatedPayload.builder()
                .bookingId("b-seat-1")
                .userId("u-1")
                .showtimeId("st-1")
                .seatIds(List.of("A1", "A2"))
                .build();
        String json = objectMapper.writeValueAsString(Map.of("payload", payload));
        ConsumerRecord<String, String> record = createRecord(Events.BOOKING_CREATED, eventId, json);

        doAnswer(inv -> {
            Runnable handler = inv.getArgument(2);
            handler.run();
            return true;
        }).when(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.BOOKING_CREATED), any(Runnable.class));

        seatKafkaConsumer.handleEvents(record);

        verify(seatService).reserveSeatsWithLock(argThat(p -> "b-seat-1".equals(p.getBookingId())));
    }

    @Test
    @DisplayName("Should route PAYMENT_PROCESSED to confirmSeats via Strategy Pattern")
    void shouldRoutePaymentProcessed() throws Exception {
        String eventId = "evt-seat-2";
        PaymentProcessedPayload payload = PaymentProcessedPayload.builder()
                .bookingId("b-seat-2")
                .paymentId("p-2")
                .build();
        String json = objectMapper.writeValueAsString(Map.of("payload", payload));
        ConsumerRecord<String, String> record = createRecord(Events.PAYMENT_PROCESSED, eventId, json);

        doAnswer(inv -> {
            Runnable handler = inv.getArgument(2);
            handler.run();
            return true;
        }).when(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.PAYMENT_PROCESSED), any(Runnable.class));

        seatKafkaConsumer.handleEvents(record);

        verify(seatService).confirmSeats("b-seat-2");
    }

    @Test
    @DisplayName("Should route PAYMENT_FAILED to compensateSeats via Strategy Pattern")
    void shouldRoutePaymentFailed() throws Exception {
        String eventId = "evt-seat-3";
        PaymentFailedPayload payload = PaymentFailedPayload.builder()
                .bookingId("b-seat-3")
                .showtimeId("st-3")
                .seatIds(List.of("A3"))
                .reason("Card declined")
                .build();
        String json = objectMapper.writeValueAsString(Map.of("payload", payload));
        ConsumerRecord<String, String> record = createRecord(Events.PAYMENT_FAILED, eventId, json);

        doAnswer(inv -> {
            Runnable handler = inv.getArgument(2);
            handler.run();
            return true;
        }).when(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.PAYMENT_FAILED), any(Runnable.class));

        seatKafkaConsumer.handleEvents(record);

        verify(seatService).compensateSeats(eq("b-seat-3"), eq("st-3"), eq(List.of("A3")), eq("Card declined"));
    }
}
