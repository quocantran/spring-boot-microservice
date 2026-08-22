package com.moviebooking.seat.consumer.handler;

import com.moviebooking.common.event.EventHandler;
import com.moviebooking.common.event.EventPayloads.PaymentFailedPayload;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for PAYMENT_FAILED event in seat-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedHandler implements EventHandler<PaymentFailedPayload> {

    private final SeatService seatService;

    @Override
    public String supportedEventType() {
        return Events.PAYMENT_FAILED;
    }

    @Override
    public Class<PaymentFailedPayload> payloadType() {
        return PaymentFailedPayload.class;
    }

    @Override
    public void handle(PaymentFailedPayload payload) {
        log.info("[Strategy] Handling PaymentFailed (compensate seats) for bookingId: {}, reason: {}",
                payload.getBookingId(), payload.getReason());
        seatService.compensateSeats(
                payload.getBookingId(),
                payload.getShowtimeId(),
                payload.getSeatIds(),
                payload.getReason()
        );
    }
}
