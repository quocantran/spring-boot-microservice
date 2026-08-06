package com.moviebooking.recommender;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.moviebooking.recommender", "com.moviebooking.common"})
@EnableJpaRepositories(basePackages = {"com.moviebooking.recommender", "com.moviebooking.common"})
@EntityScan(basePackages = {"com.moviebooking.recommender", "com.moviebooking.common"})
public class AiRecommenderApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiRecommenderApplication.class, args);
    }
}
