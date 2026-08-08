package com.moviebooking.movie.repository;

import com.moviebooking.movie.entity.ShowtimeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShowtimeRepository extends JpaRepository<ShowtimeEntity, String> {

    List<ShowtimeEntity> findByMovieIdOrderByStartTimeAsc(String movieId);
}
