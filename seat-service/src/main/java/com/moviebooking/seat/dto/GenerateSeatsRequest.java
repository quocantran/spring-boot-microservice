package com.moviebooking.seat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateSeatsRequest {

    @NotBlank(message = "showtimeId không được để trống")
    private String showtimeId;

    @Builder.Default
    private Integer rows = 5;

    @Builder.Default
    private Integer cols = 8;
}
