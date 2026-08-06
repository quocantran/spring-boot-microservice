package com.moviebooking.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBookingRequest {

    @NotNull(message = "movieId không được để trống")
    private String movieId;

    @NotNull(message = "showtimeId không được để trống")
    private String showtimeId;

    @NotEmpty(message = "Vui lòng chọn ít nhất 1 ghế")
    private List<String> seatIds;

    @NotNull(message = "totalAmount không được để trống")
    private BigDecimal totalAmount;
}
