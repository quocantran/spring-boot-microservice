package com.moviebooking.seat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.moviebooking.seat", "com.moviebooking.common"})
@EnableJpaRepositories(basePackages = {"com.moviebooking.seat", "com.moviebooking.common"})
@EntityScan(basePackages = {"com.moviebooking.seat", "com.moviebooking.common"})
@EnableScheduling
public class SeatApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeatApplication.class, args);
    }
}
