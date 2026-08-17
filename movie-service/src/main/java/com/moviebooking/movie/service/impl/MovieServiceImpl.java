package com.moviebooking.movie.service.impl;

import com.moviebooking.common.constants.CacheConstants;
import com.moviebooking.common.constants.MovieConstants;
import com.moviebooking.common.constants.SeatConstants;
import com.moviebooking.common.event.EventTypes.AggregateTypes;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.event.EventPayloads.MovieCreatedPayload;
import com.moviebooking.common.exception.CustomExceptions.BadRequestException;
import com.moviebooking.common.exception.CustomExceptions.NotFoundException;
import com.moviebooking.common.outbox.OutboxService;
import com.moviebooking.common.outbox.OutboxService.OutboxEventData;
import com.moviebooking.common.redis.RedisLockService;
import com.moviebooking.movie.dto.CreateMovieRequest;
import com.moviebooking.movie.dto.CreateShowtimeRequest;
import com.moviebooking.movie.dto.CreateShowtimeResponse;
import com.moviebooking.movie.entity.MovieEntity;
import com.moviebooking.movie.entity.ShowtimeEntity;
import com.moviebooking.movie.repository.MovieRepository;
import com.moviebooking.movie.repository.ShowtimeRepository;
import com.moviebooking.movie.service.MovieService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository;
    private final ShowtimeRepository showtimeRepository;
    private final OutboxService outboxService;
    private final RestTemplate restTemplate;
    private final RedisLockService redisLockService;

    @Autowired(required = false)
    @Qualifier("cacheRedisTemplate")
    private RedisTemplate<String, Object> cacheRedisTemplate;

    @Value("${seat-service.url:http://localhost:5002}")
    private String seatServiceUrl;

    // Cache Penetration prevention sentinel
    private static final String CACHE_NULL_SENTINEL = CacheConstants.CACHE_NULL_SENTINEL;

    // Base TTL for movie cache (10 min)
    private static final Duration MOVIES_BASE_TTL = Duration.ofMinutes(10);

    // Base TTL for showtime cache (5 min)
    private static final Duration SHOWTIMES_BASE_TTL = Duration.ofMinutes(5);

    // Short TTL for null sentinel (2 min)
    private static final Duration NULL_VALUE_TTL = Duration.ofMinutes(2);

    // Cache Avalanche prevention: max TTL jitter in seconds
    private static final long TTL_JITTER_MAX_SECONDS = CacheConstants.TTL_JITTER_MAX_SECONDS;

    // Cache Breakdown prevention: distributed lock TTL
    private static final long CACHE_LOCK_TTL_MS = CacheConstants.DEFAULT_CACHE_LOCK_TTL_MS;

    // Retry delay when lock acquisition fails
    private static final long CACHE_LOCK_RETRY_DELAY_MS = CacheConstants.DEFAULT_CACHE_LOCK_RETRY_DELAY_MS;

    @Override
    @Transactional(readOnly = true)
    public List<MovieEntity> findAllMovies() {
        return loadThroughCache(
                CacheConstants.KEY_MOVIES_ALL,
                CacheConstants.LOCK_CACHE_MOVIES_ALL,
                MOVIES_BASE_TTL,
                () -> movieRepository.findAllByOrderByCreatedAtDesc()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public MovieEntity findMovieById(String id) {
        MovieEntity movie = loadThroughCache(
                CacheConstants.PREFIX_MOVIES_CACHE + id,
                CacheConstants.LOCK_CACHE_MOVIES_PREFIX + id,
                MOVIES_BASE_TTL,
                () -> movieRepository.findById(id).orElse(null)
        );
        if (movie == null) {
            throw new NotFoundException("Không tìm thấy phim với ID: " + id);
        }
        return movie;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShowtimeEntity> findShowtimesByMovieId(String movieId) {
        findMovieById(movieId);
        return loadThroughCache(
                CacheConstants.PREFIX_SHOWTIMES_CACHE + movieId,
                CacheConstants.LOCK_CACHE_SHOWTIMES_PREFIX + movieId,
                SHOWTIMES_BASE_TTL,
                () -> showtimeRepository.findByMovieIdOrderByStartTimeAsc(movieId)
        );
    }

    @Override
    @Transactional
    @CacheEvict(value = "movies", allEntries = true)
    public MovieEntity createMovie(CreateMovieRequest dto) {
        if (dto.getTitle() == null || dto.getGenre() == null || dto.getDuration() == null) {
            throw new BadRequestException("Thiếu thông tin bắt buộc: title, genre, duration");
        }

        String movieId = UUID.randomUUID().toString();

        MovieEntity movie = MovieEntity.builder()
                .id(movieId)
                .title(dto.getTitle())
                .genre(dto.getGenre())
                .duration(dto.getDuration())
                .posterUrl(dto.getPosterUrl() != null ? dto.getPosterUrl() : "")
                .description(dto.getDescription() != null ? dto.getDescription() : "")
                .build();

        movieRepository.save(movie);

        MovieCreatedPayload payload = MovieCreatedPayload.builder()
                .movieId(movie.getId())
                .title(movie.getTitle())
                .genre(movie.getGenre())
                .description(movie.getDescription() != null ? movie.getDescription() : "")
                .duration(movie.getDuration())
                .posterUrl(movie.getPosterUrl())
                .build();

        outboxService.createEvent(OutboxEventData.builder()
                .aggregateType(AggregateTypes.MOVIE)
                .aggregateId(movie.getId())
                .eventType(Events.MOVIE_CREATED)
                .payload(payload)
                .build());

        log.info("Created movie with ID: {}", movie.getId());
        return movie;
    }

    @Override
    @Transactional
    @CacheEvict(value = "showtimes", allEntries = true)
    public CreateShowtimeResponse createShowtime(String movieId, CreateShowtimeRequest dto) {
        MovieEntity movie = findMovieById(movieId);

        if (dto.getHall() == null || dto.getStartTime() == null || dto.getPrice() == null) {
            throw new BadRequestException("Thiếu thông tin: hall, startTime, price");
        }

        Instant startTimeInstant;
        try {
            if (dto.getStartTime().contains("Z") || dto.getStartTime().contains("+")) {
                startTimeInstant = Instant.parse(dto.getStartTime());
            } else {
                startTimeInstant = java.time.LocalDateTime.parse(dto.getStartTime())
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant();
            }
        } catch (Exception e) {
            throw new BadRequestException("Định dạng startTime không hợp lệ (cần định dạng ISO-8601, ví dụ: 2026-08-06T19:00:00Z)");
        }

        String showtimeId = UUID.randomUUID().toString();

        ShowtimeEntity showtime = ShowtimeEntity.builder()
                .id(showtimeId)
                .movieId(movie.getId())
                .hall(dto.getHall())
                .startTime(startTimeInstant)
                .price(dto.getPrice())
                .build();

        showtimeRepository.save(showtime);

        int seatsGenerated = generateSeatsForShowtime(showtimeId);

        return CreateShowtimeResponse.builder()
                .id(showtime.getId())
                .movieId(showtime.getMovieId())
                .hall(showtime.getHall())
                .startTime(showtime.getStartTime())
                .price(showtime.getPrice())
                .createdAt(showtime.getCreatedAt())
                .updatedAt(showtime.getUpdatedAt())
                .seatsGenerated(seatsGenerated)
                .build();
    }

    // Inter-service REST call to seat-service protected by Circuit Breaker & Retry
    @CircuitBreaker(name = "seatService", fallbackMethod = "generateSeatsFallback")
    @Retry(name = "seatService")
    public int generateSeatsForShowtime(String showtimeId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> body = Map.of(
                MovieConstants.FIELD_SHOWTIME_ID, showtimeId,
                MovieConstants.FIELD_ROWS, SeatConstants.DEFAULT_ROWS,
                MovieConstants.FIELD_COLS, SeatConstants.DEFAULT_COLS
        );
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Map response = restTemplate.postForObject(seatServiceUrl + MovieConstants.ENDPOINT_SEATS_GENERATE, entity, Map.class);
        int seatsGenerated = 0;
        if (response != null && response.containsKey(MovieConstants.FIELD_GENERATED)) {
            seatsGenerated = ((Number) response.get(MovieConstants.FIELD_GENERATED)).intValue();
        }
        log.info("Generated {} seats for showtimeId: {}", seatsGenerated, showtimeId);
        return seatsGenerated;
    }

    // Fallback when circuit breaker opens or retries fail
    public int generateSeatsFallback(String showtimeId, Exception ex) {
        log.warn("Seat generation failed for showtimeId: {}. Circuit Breaker fallback activated. Error: {}",
                showtimeId, ex.getMessage());
        return 0;
    }

    // Generic Cache-Aside loader with Distributed Lock, TTL Jitter, and Null Sentinel
    @SuppressWarnings("unchecked")
    private <T> T loadThroughCache(String cacheKey, String lockKey, Duration baseTtl, Supplier<T> dbLoader) {
        if (cacheRedisTemplate == null) {
            return dbLoader.get();
        }

        try {
            // Step 1: Read from Redis Cache
            Object cached = cacheRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                if (CACHE_NULL_SENTINEL.equals(cached)) {
                    log.debug("[Anti Penetration] Cache hit null sentinel -> key: {}", cacheKey);
                    return null;
                }
                log.debug("[Cache HIT] key: {}", cacheKey);
                return (T) cached;
            }

            // Step 2: Cache Miss -> Acquire Distributed Lock (prevents Cache Breakdown)
            String lockToken = redisLockService.acquireLock(lockKey, CACHE_LOCK_TTL_MS);

            if (lockToken == null) {
                log.debug("[Anti Breakdown] Lock contention -> key: {}, waiting {}ms", cacheKey, CACHE_LOCK_RETRY_DELAY_MS);
                try {
                    Thread.sleep(CACHE_LOCK_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                cached = cacheRedisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (CACHE_NULL_SENTINEL.equals(cached)) return null;
                    return (T) cached;
                }

                return dbLoader.get();
            }

            try {
                // Step 3: Lock acquired -> Double check cache
                cached = cacheRedisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    if (CACHE_NULL_SENTINEL.equals(cached)) return null;
                    return (T) cached;
                }

                // Step 4: Single instance executes DB query
                T result = dbLoader.get();

                // Step 5: Save result to Redis with TTL Jitter or Null Sentinel
                if (result == null) {
                    cacheRedisTemplate.opsForValue().set(cacheKey, CACHE_NULL_SENTINEL, NULL_VALUE_TTL);
                    log.debug("[Anti Penetration] Cached null sentinel -> key: {}", cacheKey);
                } else {
                    long jitterSeconds = ThreadLocalRandom.current().nextLong(0, TTL_JITTER_MAX_SECONDS);
                    Duration jitteredTtl = baseTtl.plusSeconds(jitterSeconds);
                    cacheRedisTemplate.opsForValue().set(cacheKey, result, jitteredTtl);
                    log.debug("[Cache SET] key: {} with TTL: {}", cacheKey, jitteredTtl);
                }

                return result;
            } finally {
                // Step 6: Release Distributed Lock
                redisLockService.releaseLock(lockKey, lockToken);
            }

        } catch (Exception e) {
            log.warn("[Cache] Redis error -> key: {}, fallback to DB: {}", cacheKey, e.getMessage());
            return dbLoader.get();
        }
    }
}
