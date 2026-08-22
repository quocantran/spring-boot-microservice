package com.moviebooking.seat.consumer.handler;

import com.moviebooking.common.event.EventHandler;
import com.moviebooking.common.event.EventPayloads.BookingCreatedPayload;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for BOOKING_CREATED event in seat-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingCreatedHandler implements EventHandler<BookingCreatedPayload> {

    private final SeatService seatService;

    @Override
    public String supportedEventType() {
        return Events.BOOKING_CREATED;
    }

    @Override
    public Class<BookingCreatedPayload> payloadType() {
        return BookingCreatedPayload.class;
    }

    @Override
    public void handle(BookingCreatedPayload payload) {
        log.info("[Strategy] Handling BookingCreated for bookingId: {}", payload.getBookingId());
        seatService.reserveSeatsWithLock(payload);
    }
}
