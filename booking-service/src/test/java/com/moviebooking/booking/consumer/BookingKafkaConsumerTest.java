package com.moviebooking.booking.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.booking.consumer.handler.*;
import com.moviebooking.booking.service.BookingService;
import com.moviebooking.common.constants.KafkaConstants;
import com.moviebooking.common.event.EventHandlerRegistry;
import com.moviebooking.common.event.EventPayloads.*;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.idempotency.IdempotencyService;
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
class BookingKafkaConsumerTest {

    @Mock
    private BookingService bookingService;

    @Mock
    private IdempotencyService idempotencyService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private BookingKafkaConsumer bookingKafkaConsumer;

    @BeforeEach
    void setUp() {
        EventHandlerRegistry registry = EventHandlerRegistry.of(List.of(
                new SeatsReservedHandler(bookingService),
                new SeatReservationFailedHandler(bookingService),
                new PaymentProcessedHandler(bookingService),
                new PaymentFailedHandler(bookingService),
                new SeatsCompensatedHandler(bookingService)
        ));
        bookingKafkaConsumer = new BookingKafkaConsumer(registry, idempotencyService, objectMapper);
    }

    private ConsumerRecord<String, String> createRecord(String eventType, String eventId, String value) {
        RecordHeaders headers = new RecordHeaders();
        if (eventType != null) {
            headers.add(new RecordHeader(KafkaConstants.HEADER_EVENT_TYPE, eventType.getBytes(StandardCharsets.UTF_8)));
        }
        if (eventId != null) {
            headers.add(new RecordHeader(KafkaConstants.HEADER_EVENT_ID, eventId.getBytes(StandardCharsets.UTF_8)));
        }
        ConsumerRecord<String, String> record = new ConsumerRecord<>("seat.events", 0, 0L, "key", value);
        headers.forEach(h -> record.headers().add(h));
        return record;
    }

    @Test
    @DisplayName("Should route SEATS_RESERVED via Strategy Pattern")
    void shouldRouteSeatsReserved() throws Exception {
        String eventId = "evt-1";
        SeatsReservedPayload payload = SeatsReservedPayload.builder()
                .bookingId("b-1")
                .userId("u-1")
                .build();
        String json = objectMapper.writeValueAsString(Map.of("payload", payload));
        ConsumerRecord<String, String> record = createRecord(Events.SEATS_RESERVED, eventId, json);

        doAnswer(inv -> {
            Runnable handler = inv.getArgument(2);
            handler.run();
            return true;
        }).when(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.SEATS_RESERVED), any(Runnable.class));

        bookingKafkaConsumer.handleEvents(record);

        verify(bookingService).handleSeatsReserved(argThat(p -> "b-1".equals(p.getBookingId())));
    }

    @Test
    @DisplayName("Should route PAYMENT_PROCESSED via Strategy Pattern")
    void shouldRoutePaymentProcessed() throws Exception {
        String eventId = "evt-2";
        PaymentProcessedPayload payload = PaymentProcessedPayload.builder()
                .bookingId("b-2")
                .paymentId("p-2")
                .amount(100000.0)
                .build();
        String json = objectMapper.writeValueAsString(Map.of("payload", payload));
        ConsumerRecord<String, String> record = createRecord(Events.PAYMENT_PROCESSED, eventId, json);

        doAnswer(inv -> {
            Runnable handler = inv.getArgument(2);
            handler.run();
            return true;
        }).when(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.PAYMENT_PROCESSED), any(Runnable.class));

        bookingKafkaConsumer.handleEvents(record);

        verify(bookingService).handlePaymentProcessed(argThat(p -> "b-2".equals(p.getBookingId())));
    }

    @Test
    @DisplayName("Should route PAYMENT_FAILED via Strategy Pattern")
    void shouldRoutePaymentFailed() throws Exception {
        String eventId = "evt-3";
        PaymentFailedPayload payload = PaymentFailedPayload.builder()
                .bookingId("b-3")
                .reason("Insufficient funds")
                .build();
        String json = objectMapper.writeValueAsString(Map.of("payload", payload));
        ConsumerRecord<String, String> record = createRecord(Events.PAYMENT_FAILED, eventId, json);

        doAnswer(inv -> {
            Runnable handler = inv.getArgument(2);
            handler.run();
            return true;
        }).when(idempotencyService).processWithIdempotency(eq(eventId), eq(Events.PAYMENT_FAILED), any(Runnable.class));

        bookingKafkaConsumer.handleEvents(record);

        verify(bookingService).handlePaymentFailed(argThat(p -> "b-3".equals(p.getBookingId())));
    }
}
