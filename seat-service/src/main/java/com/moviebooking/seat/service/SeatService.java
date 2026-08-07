package com.moviebooking.seat.service;

import com.moviebooking.common.event.EventPayloads.BookingCreatedPayload;
import com.moviebooking.seat.entity.SeatEntity;

import java.util.List;

public interface SeatService {

    List<SeatEntity> findByShowtimeId(String showtimeId);

    void reserveSeatsWithLock(BookingCreatedPayload payload);

    void confirmSeats(String bookingId);

    void compensateSeats(String bookingId, String showtimeId, List<String> seatIds, String reason);

    List<SeatEntity> generateSeatsForShowtime(String showtimeId, Integer rows, Integer cols);

    void cleanupExpiredHolds();
}
