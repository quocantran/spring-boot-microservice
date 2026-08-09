package com.moviebooking.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reactive WebFilter that validates JWT tokens and sets Spring Security authentication context.
 * Also forwards user claims (X-User-Id, X-User-Email, X-User-Role) as headers to downstream services.
 */
@Slf4j
@Component
public class JwtAuthenticationWebFilter implements WebFilter {

    @Value("${jwt.secret:movie-booking-jwt-secret-key-2026}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String userId = claims.getSubject();
            String email = claims.get("email", String.class);
            String name = claims.get("name", String.class);
            String role = claims.get("role", String.class);

            // Build Spring Security Authentication with role
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "USER"))
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            // Forward user info as headers to downstream services
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Email", email != null ? email : "")
                    .header("X-User-Name", name != null ? name : "")
                    .header("X-User-Role", role != null ? role : "USER")
                    .build();

            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

            return chain.filter(mutatedExchange)
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        } catch (Exception e) {
            log.warn("Invalid JWT Token at Gateway: {}", e.getMessage());
            return chain.filter(exchange);
        }
    }
}
