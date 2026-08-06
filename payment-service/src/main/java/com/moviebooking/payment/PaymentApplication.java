package com.moviebooking.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.moviebooking.payment", "com.moviebooking.common"})
@EnableJpaRepositories(basePackages = {"com.moviebooking.payment", "com.moviebooking.common"})
@EntityScan(basePackages = {"com.moviebooking.payment", "com.moviebooking.common"})
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
