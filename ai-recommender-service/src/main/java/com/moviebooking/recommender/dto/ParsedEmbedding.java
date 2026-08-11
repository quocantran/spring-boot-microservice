package com.moviebooking.recommender.dto;

import java.util.List;

// Pre-parsed embedding and genres to avoid redundant JSON deserialization in scoring loops.
public record ParsedEmbedding(List<Float> embedding, List<String> genres) {
}
