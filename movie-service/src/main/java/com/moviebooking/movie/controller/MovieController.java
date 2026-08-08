package com.moviebooking.movie.controller;

import com.moviebooking.common.auth.Authenticated;
import com.moviebooking.common.auth.Roles;
import com.moviebooking.movie.dto.CreateMovieRequest;
import com.moviebooking.movie.dto.CreateShowtimeRequest;
import com.moviebooking.movie.dto.CreateShowtimeResponse;
import com.moviebooking.movie.entity.MovieEntity;
import com.moviebooking.movie.entity.ShowtimeEntity;
import com.moviebooking.movie.service.MovieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @GetMapping("/movies")
    public ResponseEntity<List<MovieEntity>> listMovies() {
        List<MovieEntity> movies = movieService.findAllMovies();
        return ResponseEntity.ok(movies);
    }

    @GetMapping("/movies/{id}")
    public ResponseEntity<MovieEntity> getMovieById(@PathVariable("id") String id) {
        MovieEntity movie = movieService.findMovieById(id);
        return ResponseEntity.ok(movie);
    }

    @GetMapping("/movies/{id}/showtimes")
    public ResponseEntity<List<ShowtimeEntity>> getShowtimesByMovie(@PathVariable("id") String id) {
        List<ShowtimeEntity> showtimes = movieService.findShowtimesByMovieId(id);
        return ResponseEntity.ok(showtimes);
    }

    @Authenticated
    @Roles("ADMIN")
    @PostMapping("/movies")
    public ResponseEntity<MovieEntity> createMovie(@Valid @RequestBody CreateMovieRequest dto) {
        MovieEntity movie = movieService.createMovie(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(movie);
    }

    @Authenticated
    @Roles("ADMIN")
    @PostMapping("/movies/{id}/showtimes")
    public ResponseEntity<CreateShowtimeResponse> createShowtime(
            @PathVariable("id") String movieId,
            @Valid @RequestBody CreateShowtimeRequest dto
    ) {
        CreateShowtimeResponse response = movieService.createShowtime(movieId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
