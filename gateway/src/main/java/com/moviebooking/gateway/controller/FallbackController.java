package com.moviebooking.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Fallback controller for Circuit Breaker.
 * When a downstream service is unavailable or the circuit is open,
 * the Gateway returns a graceful JSON error response instead of a raw 503.
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping(value = "/service-unavailable", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> serviceUnavailable(ServerWebExchange exchange) {
        String originalPath = exchange.getRequest().getPath().value();

        return Mono.just(Map.of(
                "statusCode", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Unavailable",
                "message", "Dịch vụ tạm thời không khả dụng. Vui lòng thử lại sau.",
                "path", originalPath,
                "timestamp", Instant.now().toString()
        ));
    }
}
