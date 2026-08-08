package com.moviebooking.movie.service;

import com.moviebooking.movie.dto.CreateMovieRequest;
import com.moviebooking.movie.dto.CreateShowtimeRequest;
import com.moviebooking.movie.dto.CreateShowtimeResponse;
import com.moviebooking.movie.entity.MovieEntity;
import com.moviebooking.movie.entity.ShowtimeEntity;

import java.util.List;

public interface MovieService {

    List<MovieEntity> findAllMovies();

    MovieEntity findMovieById(String id);

    List<ShowtimeEntity> findShowtimesByMovieId(String movieId);

    MovieEntity createMovie(CreateMovieRequest dto);

    CreateShowtimeResponse createShowtime(String movieId, CreateShowtimeRequest dto);
}
