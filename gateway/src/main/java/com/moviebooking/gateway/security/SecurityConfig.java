package com.moviebooking.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Spring Security configuration for the Reactive API Gateway.
 * Replaces custom interceptor-based auth with industry-standard SecurityFilterChain.
 * <p>
 * Public endpoints (no JWT required): auth, actuator, public movie/showtime listing.
 * Protected endpoints (JWT required): bookings, wallets, topup, seats, recommendations, admin movie CRUD.
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationWebFilter jwtAuthenticationWebFilter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                // Add JWT filter before authentication
                .addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                .authorizeExchange(exchanges -> exchanges
                        // ── Public Endpoints ──────────────────────────
                        // Auth endpoints (login, register)
                        .pathMatchers("/auth/**").permitAll()

                        // Public movie/showtime listing (GET only)
                        .pathMatchers(HttpMethod.GET, "/movies/**", "/showtimes/**").permitAll()

                        // Actuator endpoints (health, prometheus)
                        .pathMatchers("/actuator/**").permitAll()

                        // ── Protected Endpoints ──────────────────────
                        // Admin-only: create movie, create showtime
                        .pathMatchers(HttpMethod.POST, "/movies/**").hasRole("ADMIN")

                        // Authenticated users
                        .pathMatchers("/bookings/**").authenticated()
                        .pathMatchers("/wallets/**").authenticated()
                        .pathMatchers("/topup/**").authenticated()
                        .pathMatchers("/seats/**").authenticated()
                        .pathMatchers("/recommendations/**").authenticated()

                        // Default: deny all unmatched
                        .anyExchange().authenticated()
                )
                .build();
    }
}
