package com.moviebooking.seat.consumer.handler;

import com.moviebooking.common.event.EventHandler;
import com.moviebooking.common.event.EventPayloads.PaymentProcessedPayload;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for PAYMENT_PROCESSED event in seat-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessedHandler implements EventHandler<PaymentProcessedPayload> {

    private final SeatService seatService;

    @Override
    public String supportedEventType() {
        return Events.PAYMENT_PROCESSED;
    }

    @Override
    public Class<PaymentProcessedPayload> payloadType() {
        return PaymentProcessedPayload.class;
    }

    @Override
    public void handle(PaymentProcessedPayload payload) {
        log.info("[Strategy] Handling PaymentProcessed (confirm seats) for bookingId: {}", payload.getBookingId());
        seatService.confirmSeats(payload.getBookingId());
    }
}
