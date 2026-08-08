package com.moviebooking.movie.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShowtimeRequest {

    @NotBlank(message = "Phòng chiếu (hall) không được để trống")
    private String hall;

    @NotBlank(message = "Thời gian chiếu (startTime) không được để trống")
    private String startTime;

    @NotNull(message = "Giá vé (price) không được để trống")
    private BigDecimal price;
}
