package com.moviebooking.recommender.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenreSection {
    private String genre;
    private List<RecommendedMovie> movies;
}
