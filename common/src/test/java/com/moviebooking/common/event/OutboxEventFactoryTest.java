package com.moviebooking.common.event;

import com.moviebooking.common.event.EventPayloads.*;
import com.moviebooking.common.event.EventTypes.AggregateTypes;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.outbox.OutboxService.OutboxEventData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventFactoryTest {

    @Test
    @DisplayName("Should create bookingCreated event correctly")
    void testBookingCreated() {
        BookingCreatedPayload payload = BookingCreatedPayload.builder()
                .bookingId("b-1")
                .userId("u-1")
                .movieId("m-1")
                .showtimeId("st-1")
                .seatIds(List.of("A1", "A2"))
                .totalAmount(160000.0)
                .build();

        OutboxEventData event = OutboxEventFactory.bookingCreated("b-1", payload);

        assertEquals(AggregateTypes.BOOKING, event.getAggregateType());
        assertEquals("b-1", event.getAggregateId());
        assertEquals(Events.BOOKING_CREATED, event.getEventType());
        assertEquals(payload, event.getPayload());
    }

    @Test
    @DisplayName("Should create seatsReserved event correctly")
    void testSeatsReserved() {
        SeatsReservedPayload payload = SeatsReservedPayload.builder()
                .bookingId("b-2")
                .userId("u-2")
                .showtimeId("st-2")
                .seatIds(List.of("B1"))
                .totalAmount(80000.0)
                .build();

        OutboxEventData event = OutboxEventFactory.seatsReserved("b-2", payload);

        assertEquals(AggregateTypes.SEAT, event.getAggregateType());
        assertEquals("b-2", event.getAggregateId());
        assertEquals(Events.SEATS_RESERVED, event.getEventType());
    }

    @Test
    @DisplayName("Should create paymentProcessed event correctly")
    void testPaymentProcessed() {
        PaymentProcessedPayload payload = PaymentProcessedPayload.builder()
                .bookingId("b-3")
                .paymentId("p-3")
                .amount(120000.0)
                .build();

        OutboxEventData event = OutboxEventFactory.paymentProcessed("b-3", payload);

        assertEquals(AggregateTypes.PAYMENT, event.getAggregateType());
        assertEquals("b-3", event.getAggregateId());
        assertEquals(Events.PAYMENT_PROCESSED, event.getEventType());
    }
}
