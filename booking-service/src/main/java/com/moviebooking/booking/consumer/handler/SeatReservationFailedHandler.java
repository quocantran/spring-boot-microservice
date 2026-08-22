package com.moviebooking.booking.consumer.handler;

import com.moviebooking.booking.service.BookingService;
import com.moviebooking.common.event.EventHandler;
import com.moviebooking.common.event.EventPayloads.SeatReservationFailedPayload;
import com.moviebooking.common.event.EventTypes.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for SEAT_RESERVATION_FAILED event in booking-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatReservationFailedHandler implements EventHandler<SeatReservationFailedPayload> {

    private final BookingService bookingService;

    @Override
    public String supportedEventType() {
        return Events.SEAT_RESERVATION_FAILED;
    }

    @Override
    public Class<SeatReservationFailedPayload> payloadType() {
        return SeatReservationFailedPayload.class;
    }

    @Override
    public void handle(SeatReservationFailedPayload payload) {
        log.info("[Strategy] Handling SeatReservationFailed for bookingId: {}, reason: {}",
                payload.getBookingId(), payload.getReason());
        bookingService.handleSeatReservationFailed(payload);
    }
}
