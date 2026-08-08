package com.moviebooking.movie.service.impl;

import com.moviebooking.common.event.EventTypes.AggregateTypes;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.event.EventPayloads.MovieCreatedPayload;
import com.moviebooking.common.exception.CustomExceptions.BadRequestException;
import com.moviebooking.common.exception.CustomExceptions.NotFoundException;
import com.moviebooking.common.outbox.OutboxService;
import com.moviebooking.common.outbox.OutboxService.OutboxEventData;
import com.moviebooking.movie.dto.CreateMovieRequest;
import com.moviebooking.movie.dto.CreateShowtimeRequest;
import com.moviebooking.movie.dto.CreateShowtimeResponse;
import com.moviebooking.movie.entity.MovieEntity;
import com.moviebooking.movie.entity.ShowtimeEntity;
import com.moviebooking.movie.repository.MovieRepository;
import com.moviebooking.movie.repository.ShowtimeRepository;
import com.moviebooking.movie.service.MovieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository;
    private final ShowtimeRepository showtimeRepository;
    private final OutboxService outboxService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${seat-service.url:http://localhost:5002}")
    private String seatServiceUrl;

    @Override
    @Transactional(readOnly = true)
    public List<MovieEntity> findAllMovies() {
        return movieRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public MovieEntity findMovieById(String id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy phim với ID: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShowtimeEntity> findShowtimesByMovieId(String movieId) {
        findMovieById(movieId); // Ensures movie exists
        return showtimeRepository.findByMovieIdOrderByStartTimeAsc(movieId);
    }

    @Override
    @Transactional
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

        int seatsGenerated = 0;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = Map.of("showtimeId", showtimeId, "rows", 5, "cols", 8);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            Map response = restTemplate.postForObject(seatServiceUrl + "/seats/generate", entity, Map.class);
            if (response != null && response.containsKey("generated")) {
                seatsGenerated = ((Number) response.get("generated")).intValue();
            }
            log.info("Generated {} seats for showtimeId: {}", seatsGenerated, showtimeId);
        } catch (Exception e) {
            log.warn("Failed to auto-generate seats for showtimeId: {}. Error: {}", showtimeId, e.getMessage());
        }

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
}
