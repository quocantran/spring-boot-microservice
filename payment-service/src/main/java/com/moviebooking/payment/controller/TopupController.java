package com.moviebooking.payment.controller;

import com.moviebooking.common.auth.Authenticated;
import com.moviebooking.common.auth.JwtAuthFilter;
import com.moviebooking.common.auth.JwtPayload;
import com.moviebooking.payment.entity.TopupEntity;
import com.moviebooking.payment.service.TopupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/topup")
@RequiredArgsConstructor
public class TopupController {

    private final TopupService topupService;

    @Value("${frontend.port:3000}")
    private String frontendPort;

    @Authenticated
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTopup(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body
    ) {
        JwtPayload user = (JwtPayload) request.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        BigDecimal amount = BigDecimal.valueOf(((Number) body.get("amount")).doubleValue());

        Map<String, Object> result = topupService.createTopup(user.getSub(), amount);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Tạo link thanh toán thành công");
        response.put("data", result);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify/{orderCode}")
    public void verifyTopup(
            @PathVariable("orderCode") Long orderCode,
            HttpServletResponse response
    ) throws Exception {
        try {
            String result = topupService.verifyTopup(orderCode);

            Map<String, String> redirectByResult = Map.of(
                    "PAID", "success",
                    "CANCELLED", "cancelled",
                    "EXPIRED", "expired",
                    "PENDING", "pending"
            );

            String status = redirectByResult.getOrDefault(result, "pending");
            response.sendRedirect("http://localhost:" + frontendPort + "/wallet?topup=" + status + "&orderCode=" + orderCode);
        } catch (Exception e) {
            response.sendRedirect("http://localhost:" + frontendPort + "/wallet?topup=failed&orderCode=" + orderCode);
        }
    }

    @PostMapping("/webhook")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Object> handleWebhook(@RequestBody Map<String, Object> body) {
        topupService.handleWebhook(body);
        return Map.of("success", true);
    }

    @Authenticated
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getTopupHistory(HttpServletRequest request) {
        JwtPayload user = (JwtPayload) request.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        List<TopupEntity> history = topupService.getTopupHistory(user.getSub());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Lịch sử nạp tiền");
        response.put("data", history);
        return ResponseEntity.ok(response);
    }

    @Authenticated
    @DeleteMapping("/{orderCode}")
    public ResponseEntity<Map<String, Object>> cancelTopup(
            HttpServletRequest request,
            @PathVariable("orderCode") Long orderCode
    ) {
        JwtPayload user = (JwtPayload) request.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        topupService.cancelTopup(orderCode, user.getSub());
        return ResponseEntity.ok(Map.of("message", "Huỷ giao dịch thành công"));
    }
}
