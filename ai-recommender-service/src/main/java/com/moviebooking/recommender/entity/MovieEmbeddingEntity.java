package com.moviebooking.recommender.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "movie_embeddings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieEmbeddingEntity {

    @Id
    @Column(name = "movie_id", length = 36)
    private String movieId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "JSON")
    private String genres; // Stored as JSON array string: ["Action","Drama"]

    @Column(columnDefinition = "JSON")
    private String embedding; // Stored as JSON array of numbers: [0.123, -0.456, ...]

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
