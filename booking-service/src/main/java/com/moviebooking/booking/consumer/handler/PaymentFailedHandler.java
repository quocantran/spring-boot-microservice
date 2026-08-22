package com.moviebooking.booking.consumer.handler;

import com.moviebooking.booking.service.BookingService;
import com.moviebooking.common.event.EventHandler;
import com.moviebooking.common.event.EventPayloads.PaymentFailedPayload;
import com.moviebooking.common.event.EventTypes.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for PAYMENT_FAILED event in booking-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentFailedHandler implements EventHandler<PaymentFailedPayload> {

    private final BookingService bookingService;

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
        log.info("[Strategy] Handling PaymentFailed for bookingId: {}, reason: {}",
                payload.getBookingId(), payload.getReason());
        bookingService.handlePaymentFailed(payload);
    }
}
