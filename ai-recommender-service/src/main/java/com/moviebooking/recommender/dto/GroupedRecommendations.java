package com.moviebooking.recommender.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupedRecommendations {
    private List<RecommendedMovie> topPicks;        // Top AI picks (mixed genres, highest combined score)
    private List<GenreSection> genreSections;        // Per-genre sections based on user's favorite genres
    private List<String> userGenres;                 // Genres user has watched
}
