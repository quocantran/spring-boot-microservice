package com.moviebooking.recommender.controller;

import com.moviebooking.common.auth.Authenticated;
import com.moviebooking.common.auth.JwtAuthFilter;
import com.moviebooking.common.auth.JwtPayload;
import com.moviebooking.recommender.dto.GroupedRecommendations;
import com.moviebooking.recommender.service.RecommenderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class RecommenderController {

    private final RecommenderService recommenderService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "ai-recommender-service"));
    }

    @Authenticated
    @GetMapping("/recommendations/grouped")
    public ResponseEntity<Map<String, Object>> getRecommendationsGrouped(
            HttpServletRequest request,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        JwtPayload user = (JwtPayload) request.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);

        GroupedRecommendations grouped = recommenderService.getRecommendationsGrouped(user.getSub(), limit);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("userId", user.getSub());
        response.put("topPicks", grouped.getTopPicks());
        response.put("genreSections", grouped.getGenreSections());
        response.put("userGenres", grouped.getUserGenres());

        return ResponseEntity.ok(response);
    }
}
