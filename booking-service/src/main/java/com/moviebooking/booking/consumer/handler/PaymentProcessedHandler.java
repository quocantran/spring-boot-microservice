package com.moviebooking.booking.consumer.handler;

import com.moviebooking.booking.service.BookingService;
import com.moviebooking.common.event.EventHandler;
import com.moviebooking.common.event.EventPayloads.PaymentProcessedPayload;
import com.moviebooking.common.event.EventTypes.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for PAYMENT_PROCESSED event in booking-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProcessedHandler implements EventHandler<PaymentProcessedPayload> {

    private final BookingService bookingService;

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
        log.info("[Strategy] Handling PaymentProcessed for bookingId: {}", payload.getBookingId());
        bookingService.handlePaymentProcessed(payload);
    }
}
