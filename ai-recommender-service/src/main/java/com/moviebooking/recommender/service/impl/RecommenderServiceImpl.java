package com.moviebooking.recommender.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.recommender.dto.GenreSection;
import com.moviebooking.recommender.dto.GroupedRecommendations;
import com.moviebooking.recommender.dto.RecommendedMovie;
import com.moviebooking.recommender.entity.MovieEmbeddingEntity;
import com.moviebooking.recommender.entity.UserBehaviorEntity;
import com.moviebooking.recommender.repository.MovieEmbeddingRepository;
import com.moviebooking.recommender.repository.UserBehaviorRepository;
import com.moviebooking.recommender.service.EmbeddingService;
import com.moviebooking.recommender.service.RecommenderService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommenderServiceImpl implements RecommenderService {

    private final MovieEmbeddingRepository embeddingRepository;
    private final UserBehaviorRepository behaviorRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${movie-service.url:http://localhost:5003}")
    private String movieServiceUrl;

    // ======================== SCORING CONSTANTS ========================
    // Matching NestJS RecommenderService exactly

    private static final double[][] COSINE_BONUS_TIERS = {
            {0.8, 0.10},
            {0.6, 0.05},
            {0.4, 0.02},
    };

    private static final double[][] JACCARD_BONUS_TIERS = {
            {0.6, 0.08},
            {0.3, 0.04},
            {0.1, 0.01},
    };

    private static final double MAX_RAW_SCORE = 1.18;

    // ======================== STARTUP SEED ========================

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // Delayed seed (matching NestJS setTimeout 10s)
        new Thread(() -> {
            try {
                Thread.sleep(10000);
                seedEmbeddingsIfEmpty();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void seedEmbeddingsIfEmpty() {
        long count = embeddingRepository.count();
        if (count > 0) return;

        try {
            List<Map<String, Object>> movies = fetchAllMovies();
            if (movies.isEmpty()) return;

            for (Map<String, Object> movie : movies) {
                try {
                    generateAndSaveEmbedding(
                            (String) movie.get("id"),
                            (String) movie.get("title"),
                            (String) movie.get("genre"),
                            (String) movie.get("description")
                    );
                } catch (Exception e) {
                    log.warn("Failed to seed embedding for movie: {}", movie.get("id"));
                }
            }
            log.info("Seeded {} movie embeddings", embeddingRepository.count());
        } catch (Exception e) {
            log.warn("Failed to seed embeddings: {}", e.getMessage());
        }
    }

    // ======================== PUBLIC API ========================

    @Override
    @Transactional
    public void generateAndSaveEmbedding(String movieId, String title, String genre, String description) {
        String movieText = embeddingService.createMovieText(title, genre, description);
        List<Float> embedding = embeddingService.generateEmbedding(movieText);

        if (embedding.isEmpty()) return;

        List<String> genres = Arrays.stream(genre.split(","))
                .map(String::trim)
                .filter(g -> !g.isEmpty())
                .collect(Collectors.toList());

        try {
            String genresJson = objectMapper.writeValueAsString(genres);
            String embeddingJson = objectMapper.writeValueAsString(embedding);

            Optional<MovieEmbeddingEntity> existing = embeddingRepository.findById(movieId);
            if (existing.isPresent()) {
                MovieEmbeddingEntity entity = existing.get();
                entity.setTitle(title);
                entity.setGenres(genresJson);
                entity.setEmbedding(embeddingJson);
                embeddingRepository.save(entity);
            } else {
                MovieEmbeddingEntity entity = MovieEmbeddingEntity.builder()
                        .movieId(movieId)
                        .title(title)
                        .genres(genresJson)
                        .embedding(embeddingJson)
                        .build();
                embeddingRepository.save(entity);
            }

            log.info("Saved embedding for movie: {} ({})", title, movieId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize embedding data for movie: {}", movieId, e);
        }
    }

    @Override
    @Transactional
    public void saveUserBehavior(String userId, String movieId) {
        Optional<UserBehaviorEntity> existing = behaviorRepository.findByUserIdAndMovieId(userId, movieId);
        if (existing.isPresent()) return; // Idempotent

        UserBehaviorEntity behavior = UserBehaviorEntity.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .movieId(movieId)
                .build();
        behaviorRepository.save(behavior);
        log.info("Saved user behavior: userId={}, movieId={}", userId, movieId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserBehaviorEntity> getUserHistory(String userId) {
        return behaviorRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupedRecommendations getRecommendationsGrouped(String userId, int limit) {
        GroupedRecommendations emptyResult = GroupedRecommendations.builder()
                .topPicks(Collections.emptyList())
                .genreSections(Collections.emptyList())
                .userGenres(Collections.emptyList())
                .build();

        List<UserBehaviorEntity> history = getUserHistory(userId);
        if (history.isEmpty()) return emptyResult;

        Set<String> watchedMovieIds = history.stream()
                .map(UserBehaviorEntity::getMovieId)
                .collect(Collectors.toSet());

        // Load embeddings of watched movies & count genres
        List<MovieEmbeddingEntity> watchedEmbeddings = new ArrayList<>();
        Map<String, Integer> genreCount = new LinkedHashMap<>();

        for (String movieId : watchedMovieIds) {
            embeddingRepository.findById(movieId).ifPresent(emb -> {
                watchedEmbeddings.add(emb);
                List<String> genres = parseGenres(emb.getGenres());
                for (String g : genres) {
                    genreCount.merge(g.trim(), 1, Integer::sum);
                }
            });
        }

        if (watchedEmbeddings.isEmpty()) return emptyResult;

        // Sort genres by frequency (most watched first)
        List<String> sortedGenres = genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Score ALL non-watched movies
        List<MovieEmbeddingEntity> allEmbeddings = embeddingRepository.findAll();
        List<RecommendedMovie> allScored = new ArrayList<>();

        for (MovieEmbeddingEntity emb : allEmbeddings) {
            if (watchedMovieIds.contains(emb.getMovieId())) continue;

            double[] scores = calculateCombinedScore(emb, watchedEmbeddings);
            allScored.add(RecommendedMovie.builder()
                    .movieId(emb.getMovieId())
                    .title(emb.getTitle())
                    .genres(parseGenres(emb.getGenres()))
                    .similarityScore(scores[0])
                    .cosineScore(scores[1])
                    .jaccardScore(scores[2])
                    .build());
        }

        // Sort by combined score descending
        allScored.sort((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()));

        List<RecommendedMovie> topPicks = allScored.stream().limit(limit).collect(Collectors.toList());

        // Build per-genre sections
        List<GenreSection> genreSections = new ArrayList<>();
        for (String genre : sortedGenres) {
            List<RecommendedMovie> genreMovies = allScored.stream()
                    .filter(m -> m.getGenres().stream()
                            .anyMatch(g -> g.equalsIgnoreCase(genre)))
                    .limit(8)
                    .collect(Collectors.toList());

            if (!genreMovies.isEmpty()) {
                genreSections.add(GenreSection.builder()
                        .genre(genre)
                        .movies(genreMovies)
                        .build());
            }
        }

        return GroupedRecommendations.builder()
                .topPicks(topPicks)
                .genreSections(genreSections)
                .userGenres(sortedGenres)
                .build();
    }

    // ======================== SCORING ALGORITHM ========================

    /**
     * Calculates combined similarity score = 60% avgCosine + 40% avgJaccard + tier bonuses.
     * Returns [combinedScore, cosineScore, jaccardScore] (all rounded to 4 decimals).
     * Matches NestJS RecommenderService.calculateCombinedScore exactly.
     */
    private double[] calculateCombinedScore(MovieEmbeddingEntity candidate, List<MovieEmbeddingEntity> watchedEmbeddings) {
        double totalCosine = 0;
        double totalJaccard = 0;

        List<Float> candidateEmb = parseEmbedding(candidate.getEmbedding());
        List<String> candidateGenres = parseGenres(candidate.getGenres());

        for (MovieEmbeddingEntity watched : watchedEmbeddings) {
            List<Float> watchedEmb = parseEmbedding(watched.getEmbedding());
            List<String> watchedGenres = parseGenres(watched.getGenres());

            totalCosine += embeddingService.cosineSimilarity(candidateEmb, watchedEmb);
            totalJaccard += jaccardSimilarity(candidateGenres, watchedGenres);
        }

        double avgCosine = totalCosine / watchedEmbeddings.size();
        double avgJaccard = totalJaccard / watchedEmbeddings.size();

        double baseScore = 0.6 * avgCosine + 0.4 * avgJaccard;

        double cosineBonus = getTierBonus(avgCosine, COSINE_BONUS_TIERS);
        double jaccardBonus = getTierBonus(avgJaccard, JACCARD_BONUS_TIERS);

        double rawScore = baseScore + cosineBonus + jaccardBonus;
        double normalizedScore = rawScore / MAX_RAW_SCORE;

        return new double[]{
                Math.round(normalizedScore * 10000.0) / 10000.0,
                Math.round(avgCosine * 10000.0) / 10000.0,
                Math.round(avgJaccard * 10000.0) / 10000.0,
        };
    }

    private double jaccardSimilarity(List<String> genresA, List<String> genresB) {
        Set<String> setA = genresA.stream().map(String::toLowerCase).collect(Collectors.toSet());
        Set<String> setB = genresB.stream().map(String::toLowerCase).collect(Collectors.toSet());

        if (setA.isEmpty() && setB.isEmpty()) return 0;

        long intersection = setA.stream().filter(setB::contains).count();

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0.0 : (double) intersection / union.size();
    }

    private double getTierBonus(double value, double[][] tiers) {
        for (double[] tier : tiers) {
            if (value > tier[0]) return tier[1];
        }
        return 0;
    }

    // ======================== HELPERS ========================

    private List<String> parseGenres(String genresJson) {
        if (genresJson == null || genresJson.isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(genresJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private List<Float> parseEmbedding(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isEmpty()) return Collections.emptyList();
        try {
            return objectMapper.readValue(embeddingJson, new TypeReference<List<Float>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Inter-service REST call to movie-service.
     * Protected by Resilience4j Circuit Breaker + Retry.
     */
    @SuppressWarnings("unchecked")
    @CircuitBreaker(name = "movieService", fallbackMethod = "fetchAllMoviesFallback")
    @Retry(name = "movieService")
    public List<Map<String, Object>> fetchAllMovies() {
        ResponseEntity<List> response = restTemplate.getForEntity(movieServiceUrl + "/movies", List.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return (List<Map<String, Object>>) response.getBody();
        }
        return Collections.emptyList();
    }

    /**
     * Fallback when movie-service is unavailable.
     */
    public List<Map<String, Object>> fetchAllMoviesFallback(Exception ex) {
        log.warn("Movie-service unavailable. Circuit Breaker fallback activated: {}", ex.getMessage());
        return Collections.emptyList();
    }
}
