package com.moviebooking.movie.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShowtimeResponse {
    private String id;
    private String movieId;
    private String hall;
    private Instant startTime;
    private BigDecimal price;
    private Instant createdAt;
    private Instant updatedAt;
    private int seatsGenerated;
}
