package com.moviebooking.booking.service;

import com.moviebooking.booking.dto.CreateBookingRequest;
import com.moviebooking.booking.entity.BookingEntity;
import com.moviebooking.common.event.EventPayloads.*;

import java.util.List;

public interface BookingService {

    BookingEntity createBooking(String userId, CreateBookingRequest request);

    List<BookingEntity> getBookingsByUser(String userId);

    BookingEntity getBookingById(String id);

    void handleSeatsReserved(SeatsReservedPayload payload);

    void handleSeatReservationFailed(SeatReservationFailedPayload payload);

    void handlePaymentProcessed(PaymentProcessedPayload payload);

    void handlePaymentFailed(PaymentFailedPayload payload);

    void handleSeatsCompensated(SeatsCompensatedPayload payload);
}
