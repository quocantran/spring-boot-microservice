package com.moviebooking.movie.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "showtimes", indexes = {
        @Index(name = "idx_showtime_movie", columnList = "movie_id"),
        @Index(name = "idx_showtime_start_time", columnList = "start_time")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShowtimeEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "movie_id", nullable = false, length = 36)
    private String movieId;

    @Column(nullable = false, length = 50)
    private String hall;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
