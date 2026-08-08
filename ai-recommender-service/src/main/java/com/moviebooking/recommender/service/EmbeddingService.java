package com.moviebooking.recommender.service;

import java.util.List;

/**
 * Service interface for generating text embeddings using AI models.
 * Production implementation uses DJL (Deep Java Library) with ONNX Runtime
 * to run the all-MiniLM-L6-v2 sentence-transformer model locally.
 */
public interface EmbeddingService {

    /**
     * Generate a vector embedding for the given text.
     * @param text input text to embed (max ~5000 chars)
     * @return float array of embedding values, or empty list on failure
     */
    List<Float> generateEmbedding(String text);

    /**
     * Create descriptive text from movie metadata for embedding generation.
     * Matches NestJS: title + "Thể loại: genre" + description×3
     */
    String createMovieText(String title, String genre, String description);

    /**
     * Compute cosine similarity between two vectors.
     */
    double cosineSimilarity(List<Float> vecA, List<Float> vecB);
}
