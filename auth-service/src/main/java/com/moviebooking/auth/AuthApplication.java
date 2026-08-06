package com.moviebooking.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.moviebooking.auth", "com.moviebooking.common"})
@EnableJpaRepositories(basePackages = {"com.moviebooking.auth", "com.moviebooking.common"})
@EntityScan(basePackages = {"com.moviebooking.auth", "com.moviebooking.common"})
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
