package com.moviebooking.common.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${jwt.secret:movie-booking-jwt-secret-key-2026}")
    private String jwtSecret;

    public static final String USER_ATTRIBUTE = "user";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                JwtPayload payload = verifyToken(token);
                request.setAttribute(USER_ATTRIBUTE, payload);
            } catch (Exception e) {
                log.warn("Invalid JWT Token: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    public JwtPayload verifyToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return JwtPayload.builder()
                .sub(claims.getSubject())
                .email(claims.get("email", String.class))
                .name(claims.get("name", String.class))
                .role(claims.get("role", String.class))
                .build();
    }
}
