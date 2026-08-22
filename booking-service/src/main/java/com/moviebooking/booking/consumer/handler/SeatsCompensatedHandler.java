package com.moviebooking.booking.consumer.handler;

import com.moviebooking.booking.service.BookingService;
import com.moviebooking.common.event.EventHandler;
import com.moviebooking.common.event.EventPayloads.SeatsCompensatedPayload;
import com.moviebooking.common.event.EventTypes.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for SEATS_COMPENSATED event in booking-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatsCompensatedHandler implements EventHandler<SeatsCompensatedPayload> {

    private final BookingService bookingService;

    @Override
    public String supportedEventType() {
        return Events.SEATS_COMPENSATED;
    }

    @Override
    public Class<SeatsCompensatedPayload> payloadType() {
        return SeatsCompensatedPayload.class;
    }

    @Override
    public void handle(SeatsCompensatedPayload payload) {
        log.info("[Strategy] Handling SeatsCompensated for bookingId: {}", payload.getBookingId());
        bookingService.handleSeatsCompensated(payload);
    }
}
