package com.moviebooking.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.moviebooking.booking", "com.moviebooking.common"})
@EnableJpaRepositories(basePackages = {"com.moviebooking.booking", "com.moviebooking.common"})
@EntityScan(basePackages = {"com.moviebooking.booking", "com.moviebooking.common"})
public class BookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingApplication.class, args);
    }
}
