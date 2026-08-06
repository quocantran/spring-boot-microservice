package com.moviebooking.movie;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.moviebooking.movie", "com.moviebooking.common"})
@EnableJpaRepositories(basePackages = {"com.moviebooking.movie", "com.moviebooking.common"})
@EntityScan(basePackages = {"com.moviebooking.movie", "com.moviebooking.common"})
public class MovieApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieApplication.class, args);
    }
}
