package com.moviebooking.recommender.repository;

import com.moviebooking.recommender.entity.UserBehaviorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserBehaviorRepository extends JpaRepository<UserBehaviorEntity, String> {

    List<UserBehaviorEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<UserBehaviorEntity> findByUserIdAndMovieId(String userId, String movieId);
}
