package com.moviebooking.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/**
 * Rate Limiting configuration for API Gateway.
 * Uses Redis-backed RequestRateLimiter with user-based key resolution.
 * <p>
 * Key resolution priority:
 * 1. Authenticated user ID (from JWT → X-User-Id header)
 * 2. Client IP address (fallback for unauthenticated requests)
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // Prefer authenticated user ID, fallback to IP
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isEmpty()) {
                return Mono.just(userId);
            }

            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "anonymous";
            return Mono.just(ip);
        };
    }
}
