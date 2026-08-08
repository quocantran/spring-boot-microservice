package com.moviebooking.recommender.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "user_behavior", indexes = {
        @Index(name = "idx_behavior_user", columnList = "user_id"),
        @Index(name = "idx_behavior_movie", columnList = "movie_id"),
        @Index(name = "idx_behavior_user_movie", columnList = "user_id, movie_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBehaviorEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "movie_id", nullable = false, length = 36)
    private String movieId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
