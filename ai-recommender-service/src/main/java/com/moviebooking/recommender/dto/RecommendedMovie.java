package com.moviebooking.recommender.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendedMovie {
    private String movieId;
    private String title;
    private List<String> genres;
    private double similarityScore;   // Combined score (60% cosine + 40% jaccard)
    private double cosineScore;        // Cosine similarity on description embedding
    private double jaccardScore;       // Jaccard similarity on genres
}
