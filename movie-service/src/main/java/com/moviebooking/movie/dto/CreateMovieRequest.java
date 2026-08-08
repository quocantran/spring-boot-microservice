package com.moviebooking.movie.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMovieRequest {

    @NotBlank(message = "Tên phim (title) không được để trống")
    private String title;

    @NotBlank(message = "Thể loại (genre) không được để trống")
    private String genre;

    @NotNull(message = "Thời lượng (duration) không được để trống")
    @Min(value = 1, message = "Thời lượng phải lớn hơn 0")
    private Integer duration;

    private String posterUrl;
    private String description;
}
