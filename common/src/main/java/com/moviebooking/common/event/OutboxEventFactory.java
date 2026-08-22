package com.moviebooking.common.event;

import com.moviebooking.common.event.EventPayloads.*;
import com.moviebooking.common.event.EventTypes.AggregateTypes;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.outbox.OutboxService.OutboxEventData;

import java.util.Map;

/**
 * Factory Method Pattern for creating Outbox Events across microservices.
 * Eliminates repetitive OutboxEventData builder boilerplate and centralizes event structure contracts.
 */
public final class OutboxEventFactory {

    private OutboxEventFactory() {}

    // ================= Booking Events =================

    public static OutboxEventData bookingCreated(String bookingId, BookingCreatedPayload payload) {
        return OutboxEventData.builder()
                .aggregateType(AggregateTypes.BOOKING)
                .aggregateId(bookingId)
                .eventType(Events.BOOKING_CREATED)
                .payload(payload)
                .build();
    }

    public static OutboxEventData bookingConfirmed(String bookingId, BookingConfirmedPayload payload) {
        return OutboxEventData.builder()
                .aggregateType(AggregateTypes.BOOKING)
                .aggregateId(bookingId)
                .eventType(Events.BOOKING_CONFIRMED)
                .payload(payload)
                .build();
    }

    public static OutboxEventData bookingCancelled(String bookingId, BookingCancelledPayload payload) {
        return OutboxEventData.builder()
                .aggregateType(AggregateTypes.BOOKING)
                .aggregateId(bookingId)
                .eventType(Events.BOOKING_CANCELLED)
                .payload(payload)
                .build();
    }

    // ================= Seat Events =================

    public static OutboxEventData seatsReserved(String bookingId, SeatsReservedPayload payload) {
        return OutboxEventData.builder()
                .aggregateType(AggregateTypes.SEAT)
                .aggregateId(bookingId)
                .eventType(Events.SEATS_RESERVED)
                .payload(payload)
                .build();
    }

    public static OutboxEventData seatReservationFailed(String bookingId, SeatReservationFailedPayload payload) {
        return OutboxEventData.builder()
                .aggregateType(AggregateTypes.SEAT)
                .aggregateId(bookingId)
                .eventType(Events.SEAT_RESERVATION_FAILED)
                .payload(payload)
                .build();
    }

    public static OutboxEventData seatsConfirmed(String bookingId, Map<String, Object> payload) {
        return OutboxEventData.builder()
                .aggregateType(AggregateTypes.SEAT)
                .aggregateId(bookingId)
                .eventType(Events.SEATS_CONFIRMED)
                .payload(payload)
                .build();
    }

    public static OutboxEventData seatsCompensated(String bookingId, SeatsCompensatedPayload payload) {
        return OutboxEventData.builder()
                .aggregateType(AggregateTypes.SEAT)
                .aggregateId(bookingId)
                .eventType(Events.SEATS_COMPENSATED)
                .payload(payload)
                .build();
    }

    // ================= Payment Events =================

    public static OutboxEventData paymentProcessed(String bookingId, PaymentProcessedPayload payload) {
        return OutboxEventData.builder()
                .aggregateType(AggregateTypes.PAYMENT)
                .aggregateId(bookingId)
                .eventType(Events.PAYMENT_PROCESSED)
                .payload(payload)
                .build();
    }

    public static OutboxEventData paymentFailed(String bookingId, PaymentFailedPayload payload) {
        return OutboxEventData.builder()
                .aggregateType(AggregateTypes.PAYMENT)
                .aggregateId(bookingId)
                .eventType(Events.PAYMENT_FAILED)
                .payload(payload)
                .build();
    }

    // ================= Movie Events =================

    public static OutboxEventData movieCreated(String movieId, MovieCreatedPayload payload) {
        return OutboxEventData.builder()
                .aggregateType(AggregateTypes.MOVIE)
                .aggregateId(movieId)
                .eventType(Events.MOVIE_CREATED)
                .payload(payload)
                .build();
    }
}
