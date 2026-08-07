package com.moviebooking.seat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "seats",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_showtime_seat_number", columnNames = {"showtime_id", "seat_number"})
        },
        indexes = {
                @Index(name = "idx_seat_showtime", columnList = "showtime_id"),
                @Index(name = "idx_seat_status", columnList = "status"),
                @Index(name = "idx_seat_showtime_id", columnList = "showtime_id, id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "showtime_id", nullable = false, length = 36)
    private String showtimeId;

    @Column(name = "seat_number", nullable = false, length = 10)
    private String seatNumber;

    @Column(name = "seat_row", nullable = false, length = 5)
    private String seatRow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SeatStatus status = SeatStatus.AVAILABLE;

    @Column(name = "booking_id", length = 36)
    private String bookingId;

    @Column(name = "expire_at")
    private Instant expireAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
