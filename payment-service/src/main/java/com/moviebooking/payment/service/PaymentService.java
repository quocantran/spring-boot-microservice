package com.moviebooking.payment.service;

import com.moviebooking.common.event.EventPayloads.SeatsReservedPayload;

public interface PaymentService {

    void processPayment(SeatsReservedPayload payload);
}
