package com.moviebooking.recommender.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.recommender.dto.GenreSection;
import com.moviebooking.recommender.dto.GroupedRecommendations;
import com.moviebooking.recommender.dto.ParsedEmbedding;
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
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    // Scoring tiers matching recommendation strategy

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

    // Triggers embedding seed asynchronously using a lightweight Virtual Thread.
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(10000);
                seedEmbeddingsIfEmpty();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void seedEmbeddingsIfEmpty() {
        long count = embeddingRepository.count();
        if (count > 0) return;
        syncEmbeddingsFromMovieService();
    }

    // Syncs movie embeddings concurrently using a Virtual Thread per-task executor.
    private void syncEmbeddingsFromMovieService() {
        try {
            List<Map<String, Object>> movies = fetchAllMovies();
            if (movies.isEmpty()) return;

            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<?>> futures = new ArrayList<>();

                for (Map<String, Object> movie : movies) {
                    futures.add(executor.submit(() -> {
                        try {
                            String id = (String) movie.get("id");
                            String title = (String) movie.get("title");
                            String genre = (String) movie.get("genre");
                            String description = (String) movie.get("description");
                            if (id != null && title != null) {
                                generateAndSaveEmbedding(
                                        id,
                                        title,
                                        genre != null ? genre : "",
                                        description != null ? description : ""
                                );
                            }
                        } catch (Exception e) {
                            log.warn("Failed to seed embedding for movie: {}", movie.get("id"));
                        }
                    }));
                }

                // Awaits completion of all seed tasks with 60s timeout.
                for (Future<?> f : futures) {
                    try {
                        f.get(60, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.warn("Seed task timed out or failed: {}", e.getMessage());
                    }
                }
            }

            log.info("Seeded {} movie embeddings (parallel Virtual Threads)", embeddingRepository.count());
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
        if (existing.isPresent()) return; // Idempotent check

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

    // Retrieves grouped recommendations optimized with batch queries and parallel scoring.
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

        // Batch fetch watched embeddings in a single SQL query.
        List<MovieEmbeddingEntity> watchedEmbeddings = embeddingRepository.findAllById(watchedMovieIds);

        // Count genre frequencies from watched movies.
        Map<String, Integer> genreCount = new LinkedHashMap<>();
        for (MovieEmbeddingEntity emb : watchedEmbeddings) {
            List<String> genres = parseGenres(emb.getGenres());
            for (String g : genres) {
                genreCount.merge(g.trim(), 1, Integer::sum);
            }
        }

        // Fallback: sync embeddings from movie-service if missing.
        if (watchedEmbeddings.isEmpty() || embeddingRepository.count() == 0) {
            syncEmbeddingsFromMovieService();
            watchedEmbeddings = embeddingRepository.findAllById(watchedMovieIds);
            for (MovieEmbeddingEntity emb : watchedEmbeddings) {
                List<String> genres = parseGenres(emb.getGenres());
                for (String g : genres) {
                    genreCount.merge(g.trim(), 1, Integer::sum);
                }
            }
        }

        if (watchedEmbeddings.isEmpty()) return emptyResult;

        // Sort genres by frequency descending.
        List<String> sortedGenres = genreCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // Pre-parse watched embeddings once to eliminate redundant JSON parsing.
        List<ParsedEmbedding> parsedWatched = watchedEmbeddings.stream()
                .map(w -> new ParsedEmbedding(parseEmbedding(w.getEmbedding()), parseGenres(w.getGenres())))
                .toList();

        // Compute similarity scores in parallel across all CPU cores.
        List<MovieEmbeddingEntity> allEmbeddings = embeddingRepository.findAll();

        List<RecommendedMovie> allScored = allEmbeddings.parallelStream()
                .filter(emb -> !watchedMovieIds.contains(emb.getMovieId()))
                .map(emb -> {
                    double[] scores = calculateCombinedScoreOptimized(emb, parsedWatched);
                    return RecommendedMovie.builder()
                            .movieId(emb.getMovieId())
                            .title(emb.getTitle())
                            .genres(parseGenres(emb.getGenres()))
                            .similarityScore(scores[0])
                            .cosineScore(scores[1])
                            .jaccardScore(scores[2])
                            .build();
                })
                .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                .toList();

        List<RecommendedMovie> topPicks = allScored.stream().limit(limit).collect(Collectors.toList());

        // Build per-genre recommendation sections.
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

    // Calculates combined score (60% cosine + 40% jaccard) using pre-parsed embeddings.
    private double[] calculateCombinedScoreOptimized(MovieEmbeddingEntity candidate, List<ParsedEmbedding> parsedWatched) {
        double totalCosine = 0;
        double totalJaccard = 0;

        List<Float> candidateEmb = parseEmbedding(candidate.getEmbedding());
        List<String> candidateGenres = parseGenres(candidate.getGenres());

        for (ParsedEmbedding watched : parsedWatched) {
            totalCosine += embeddingService.cosineSimilarity(candidateEmb, watched.embedding());
            totalJaccard += jaccardSimilarity(candidateGenres, watched.genres());
        }

        double avgCosine = totalCosine / parsedWatched.size();
        double avgJaccard = totalJaccard / parsedWatched.size();

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

    // Calculates combined score (60% cosine + 40% jaccard) using raw entities.
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

    // Inter-service REST call to movie-service protected by Resilience4j CircuitBreaker and Retry.
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

    // Fallback method when movie-service REST call fails.
    public List<Map<String, Object>> fetchAllMoviesFallback(Exception ex) {
        log.warn("Movie-service unavailable. Circuit Breaker fallback activated: {}", ex.getMessage());
        return Collections.emptyList();
    }
}
