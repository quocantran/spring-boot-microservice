package com.moviebooking.seat.repository;

import com.moviebooking.seat.entity.SeatEntity;
import com.moviebooking.seat.entity.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface SeatRepository extends JpaRepository<SeatEntity, String> {

    List<SeatEntity> findByShowtimeIdOrderBySeatRowAscSeatNumberAsc(String showtimeId);

    List<SeatEntity> findByBookingId(String bookingId);

    @Modifying
    @Query("UPDATE SeatEntity s SET s.status = :status, s.bookingId = :bookingId, s.expireAt = :expireAt " +
            "WHERE s.id IN :seatIds AND s.showtimeId = :showtimeId AND s.status = :availableStatus")
    int reserveAvailableSeats(
            @Param("seatIds") List<String> seatIds,
            @Param("showtimeId") String showtimeId,
            @Param("bookingId") String bookingId,
            @Param("expireAt") Instant expireAt,
            @Param("status") SeatStatus status,
            @Param("availableStatus") SeatStatus availableStatus
    );

    @Modifying
    @Query("UPDATE SeatEntity s SET s.status = :availableStatus, s.bookingId = null, s.expireAt = null " +
            "WHERE s.bookingId = :bookingId AND s.status = :heldStatus")
    int rollbackHeldSeats(
            @Param("bookingId") String bookingId,
            @Param("heldStatus") SeatStatus heldStatus,
            @Param("availableStatus") SeatStatus availableStatus
    );

    @Modifying
    @Query("UPDATE SeatEntity s SET s.status = :bookedStatus, s.expireAt = null " +
            "WHERE s.bookingId = :bookingId AND s.status = :heldStatus AND (s.expireAt IS NULL OR s.expireAt > :now)")
    int confirmHeldSeats(
            @Param("bookingId") String bookingId,
            @Param("now") Instant now,
            @Param("heldStatus") SeatStatus heldStatus,
            @Param("bookedStatus") SeatStatus bookedStatus
    );

    @Modifying
    @Query("UPDATE SeatEntity s SET s.status = :availableStatus, s.bookingId = null, s.expireAt = null " +
            "WHERE s.id IN :seatIds AND s.bookingId = :bookingId")
    int compensateSeats(
            @Param("seatIds") List<String> seatIds,
            @Param("bookingId") String bookingId,
            @Param("availableStatus") SeatStatus availableStatus
    );

    @Modifying
    @Query("UPDATE SeatEntity s SET s.status = :availableStatus, s.bookingId = null, s.expireAt = null " +
            "WHERE s.status = :heldStatus AND s.expireAt IS NOT NULL AND s.expireAt < :now")
    int cleanupExpiredHolds(
            @Param("now") Instant now,
            @Param("heldStatus") SeatStatus heldStatus,
            @Param("availableStatus") SeatStatus availableStatus
    );
}
