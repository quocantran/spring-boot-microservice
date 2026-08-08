package com.moviebooking.recommender.repository;

import com.moviebooking.recommender.entity.MovieEmbeddingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieEmbeddingRepository extends JpaRepository<MovieEmbeddingEntity, String> {
}
