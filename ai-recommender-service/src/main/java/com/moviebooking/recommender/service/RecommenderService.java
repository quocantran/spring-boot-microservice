package com.moviebooking.recommender.service;

import com.moviebooking.recommender.dto.GroupedRecommendations;
import com.moviebooking.recommender.entity.UserBehaviorEntity;

import java.util.List;

public interface RecommenderService {

    void generateAndSaveEmbedding(String movieId, String title, String genre, String description);

    void saveUserBehavior(String userId, String movieId);

    List<UserBehaviorEntity> getUserHistory(String userId);

    GroupedRecommendations getRecommendationsGrouped(String userId, int limit);
}
