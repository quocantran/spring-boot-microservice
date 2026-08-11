package com.moviebooking.gateway.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Spring Security configuration for the Reactive API Gateway.
 * Replaces custom interceptor-based auth with industry-standard SecurityFilterChain.
 * <p>
 * Public endpoints (no JWT required): auth, actuator, public movie/showtime/seat listing.
 * Protected endpoints (JWT required): bookings, wallets, topup, recommendations, admin movie CRUD.
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationWebFilter jwtAuthenticationWebFilter;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        ServerAuthenticationEntryPoint authenticationEntryPoint = (exchange, ex) -> {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] bytes = "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}"
                    .getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        };

        ServerAccessDeniedHandler accessDeniedHandler = (exchange, denied) -> {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] bytes = "{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied\"}"
                    .getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        };

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)

                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )

                // Add JWT filter before authentication
                .addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                .authorizeExchange(exchanges -> exchanges
                        // ── Public Endpoints ──────────────────────────
                        // Auth endpoints (login, register)
                        .pathMatchers("/auth/**").permitAll()

                        // Public movie/showtime listing & details (GET only)
                        .pathMatchers(HttpMethod.GET, "/movies/**", "/showtimes/**").permitAll()

                        // Public seat maps & SSE live stream (GET only)
                        .pathMatchers(HttpMethod.GET, "/seats/**").permitAll()

                        // Real-time SSE streams for bookings & wallets (GET only)
                        .pathMatchers(HttpMethod.GET, "/bookings/*/stream", "/bookings/stream", "/wallets/stream").permitAll()

                        // Actuator endpoints (health, prometheus)
                        .pathMatchers("/actuator/**").permitAll()

                        // ── Protected Endpoints ──────────────────────
                        // Admin-only: create movie, create showtime
                        .pathMatchers(HttpMethod.POST, "/movies/**").hasRole("ADMIN")

                        // Authenticated users
                        .pathMatchers("/bookings/**").authenticated()
                        .pathMatchers("/wallets/**").authenticated()
                        .pathMatchers("/topup/**").authenticated()
                        .pathMatchers("/recommendations/**").authenticated()

                        // Default: deny all unmatched
                        .anyExchange().authenticated()
                )
                .build();
    }
}
