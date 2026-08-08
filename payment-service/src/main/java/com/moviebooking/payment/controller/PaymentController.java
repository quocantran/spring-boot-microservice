package com.moviebooking.payment.controller;

import com.moviebooking.common.auth.Authenticated;
import com.moviebooking.common.auth.JwtAuthFilter;
import com.moviebooking.common.auth.JwtPayload;
import com.moviebooking.payment.entity.WalletEntity;
import com.moviebooking.payment.repository.WalletRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class PaymentController {

    private final WalletRepository walletRepository;

    private static final BigDecimal DEFAULT_WALLET_BALANCE = BigDecimal.valueOf(200000);

    @Authenticated
    @GetMapping("/wallets/me")
    public ResponseEntity<Map<String, Object>> getMyWallet(HttpServletRequest request) {
        JwtPayload user = (JwtPayload) request.getAttribute(JwtAuthFilter.USER_ATTRIBUTE);
        Optional<WalletEntity> walletOpt = walletRepository.findById(user.getSub());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getSub());
        result.put("balance", walletOpt.map(w -> w.getBalance().doubleValue()).orElse(0.0));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/wallets")
    public ResponseEntity<Map<String, Object>> createWallet(@RequestBody Map<String, Object> body) {
        String userId = (String) body.get("userId");
        BigDecimal initialBalance = body.containsKey("initialBalance")
                ? BigDecimal.valueOf(((Number) body.get("initialBalance")).doubleValue())
                : DEFAULT_WALLET_BALANCE;

        Optional<WalletEntity> existing = walletRepository.findById(userId);
        Map<String, Object> result = new LinkedHashMap<>();

        if (existing.isPresent()) {
            result.put("userId", userId);
            result.put("balance", existing.get().getBalance().doubleValue());
            result.put("created", false);
            return ResponseEntity.ok(result);
        }

        WalletEntity wallet = WalletEntity.builder()
                .userId(userId)
                .balance(initialBalance)
                .build();
        walletRepository.save(wallet);

        result.put("userId", userId);
        result.put("balance", initialBalance.doubleValue());
        result.put("created", true);
        return ResponseEntity.ok(result);
    }
}
