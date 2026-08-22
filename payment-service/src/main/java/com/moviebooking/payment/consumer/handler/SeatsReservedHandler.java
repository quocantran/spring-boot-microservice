package com.moviebooking.payment.consumer.handler;

import com.moviebooking.common.event.EventHandler;
import com.moviebooking.common.event.EventPayloads.SeatsReservedPayload;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation for SEATS_RESERVED event in payment-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatsReservedHandler implements EventHandler<SeatsReservedPayload> {

    private final PaymentService paymentService;

    @Override
    public String supportedEventType() {
        return Events.SEATS_RESERVED;
    }

    @Override
    public Class<SeatsReservedPayload> payloadType() {
        return SeatsReservedPayload.class;
    }

    @Override
    public void handle(SeatsReservedPayload payload) {
        log.info("[Strategy] Handling SeatsReserved (process payment) for bookingId: {}", payload.getBookingId());
        paymentService.processPayment(payload);
    }
}
