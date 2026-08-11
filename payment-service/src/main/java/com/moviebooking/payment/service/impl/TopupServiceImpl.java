package com.moviebooking.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.exception.CustomExceptions.BadRequestException;
import com.moviebooking.common.exception.CustomExceptions.NotFoundException;
import com.moviebooking.payment.entity.TopupEntity;
import com.moviebooking.payment.entity.TopupStatus;
import com.moviebooking.payment.entity.WalletEntity;
import com.moviebooking.payment.repository.TopupRepository;
import com.moviebooking.payment.repository.WalletRepository;
import com.moviebooking.payment.service.TopupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopupServiceImpl implements TopupService {

    private final TopupRepository topupRepository;
    private final WalletRepository walletRepository;
    private final ObjectMapper objectMapper;
    private final com.moviebooking.payment.realtime.WalletRealtimePublisher walletRealtimePublisher;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${payos.url:https://api-merchant.payos.vn}")
    private String payosUrl;

    @Value("${payos.api-key:}")
    private String payosApiKey;

    @Value("${payos.client-id:}")
    private String payosClientId;

    @Value("${payos.checksum-key:}")
    private String payosChecksumKey;

    @Value("${gateway.port:8080}")
    private String gatewayPort;

    @Value("${frontend.port:3000}")
    private String frontendPort;

    // ===================== PRIVATE HELPERS =====================

    private long generateOrderCode() {
        long timestamp = System.currentTimeMillis() % 1000000000L;
        long random = (long) (Math.random() * 1000);
        String raw = String.valueOf(timestamp) + String.valueOf(random);
        String code = raw.length() > 9 ? raw.substring(0, 9) : String.format("%-9s", raw).replace(' ', '0');
        return Long.parseLong(code);
    }

    private String createSignature(String data) {
        if (payosChecksumKey == null || payosChecksumKey.trim().isEmpty()) {
            throw new BadRequestException("Chưa cấu hình PAYOS_CHECKSUM_KEY trong hệ thống");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(payosChecksumKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HMAC signature", e);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean verifyWebhookSignature(Map<String, Object> data, String signature) {
        List<String> sortedKeys = new ArrayList<>(data.keySet());
        Collections.sort(sortedKeys);

        String dataStr = sortedKeys.stream()
                .filter(key -> data.get(key) != null)
                .map(key -> {
                    Object value = data.get(key);
                    if (value == null || "null".equals(String.valueOf(value)) || "undefined".equals(String.valueOf(value))) {
                        return key + "=";
                    }
                    if (value instanceof List) {
                        try {
                            List<Object> list = (List<Object>) value;
                            List<Object> sorted = list.stream().map(val -> {
                                if (val instanceof Map) {
                                    Map<String, Object> map = (Map<String, Object>) val;
                                    return new TreeMap<>(map);
                                }
                                return val;
                            }).collect(Collectors.toList());
                            return key + "=" + objectMapper.writeValueAsString(sorted);
                        } catch (Exception e) {
                            return key + "=" + value;
                        }
                    }
                    return key + "=" + value;
                })
                .collect(Collectors.joining("&"));

        String computedSignature = createSignature(dataStr);
        return computedSignature.equals(signature);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private HttpHeaders createPayosHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", payosApiKey);
        headers.set("x-client-id", payosClientId);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchPayosPaymentRequest(Long orderCode) {
        HttpEntity<Void> entity = new HttpEntity<>(createPayosHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    payosUrl + "/v2/payment-requests/" + orderCode,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BadRequestException("Không thể kiểm tra trạng thái thanh toán");
            }

            return (Map<String, Object>) response.getBody().get("data");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Không thể kiểm tra trạng thái thanh toán: " + e.getMessage());
        }
    }

    private void markTopupStatus(Long orderCode, TopupStatus newStatus) {
        topupRepository.updateStatusByOrderCode(orderCode, TopupStatus.PENDING, newStatus);
    }

    /**
     * Core algorithm: sync local topup status with PayOS.
     * If PayOS reports PAID, we credit the wallet atomically.
     */
    @SuppressWarnings("unchecked")
    private String syncTopupFromPayos(TopupEntity topup) {
        if (topup.getStatus() == TopupStatus.PAID) return "PAID";
        if (topup.getStatus() == TopupStatus.CANCELLED) return "CANCELLED";
        if (topup.getStatus() == TopupStatus.EXPIRED) return "EXPIRED";

        Map<String, Object> payosData = fetchPayosPaymentRequest(topup.getOrderCode());
        String payosStatus = String.valueOf(payosData.getOrDefault("status", "")).toUpperCase();

        if ("PAID".equals(payosStatus)) {
            // Verify amount matches
            BigDecimal payosAmount = new BigDecimal(String.valueOf(payosData.get("amount")));
            if (payosAmount.compareTo(topup.getAmount()) != 0) {
                log.error("Amount mismatch: payOS={}, local={}, orderCode={}", payosAmount, topup.getAmount(), topup.getOrderCode());
                throw new BadRequestException("Số tiền không khớp");
            }

            // Extract transaction details
            Map<String, Object> transaction = null;
            Object transactionsObj = payosData.get("transactions");
            if (transactionsObj instanceof List) {
                List<Map<String, Object>> transactions = (List<Map<String, Object>>) transactionsObj;
                if (!transactions.isEmpty()) {
                    transaction = transactions.get(0);
                }
            }

            creditWallet(topup, transaction);
            return "PAID";
        }

        if ("CANCELLED".equals(payosStatus)) {
            markTopupStatus(topup.getOrderCode(), TopupStatus.CANCELLED);
            return "CANCELLED";
        }

        if ("EXPIRED".equals(payosStatus)) {
            markTopupStatus(topup.getOrderCode(), TopupStatus.EXPIRED);
            return "EXPIRED";
        }

        return "PENDING";
    }

    /**
     * Atomically mark topup as PAID and credit the user's wallet.
     * Uses conditional UPDATE (WHERE status = PENDING) for idempotency.
     */
    @Transactional
    public void creditWallet(TopupEntity topup, Map<String, Object> transaction) {
        String reference = transaction != null ? (String) transaction.get("reference") : null;
        String counterAccountBankName = transaction != null ? (String) transaction.get("counterAccountBankName") : null;
        String counterAccountName = transaction != null ? (String) transaction.get("counterAccountName") : null;
        String counterAccountNumber = transaction != null ? (String) transaction.get("counterAccountNumber") : null;
        String transactionDateTime = transaction != null ? (String) transaction.get("transactionDateTime") : null;

        Instant paidAt;
        try {
            paidAt = transactionDateTime != null ? Instant.parse(transactionDateTime) : Instant.now();
        } catch (Exception e) {
            paidAt = Instant.now();
        }

        // Atomic: only update if still PENDING (idempotency guard)
        int affected = topupRepository.markAsPaid(
                topup.getOrderCode(),
                TopupStatus.PENDING,
                TopupStatus.PAID,
                reference,
                counterAccountBankName,
                counterAccountName,
                counterAccountNumber,
                paidAt
        );

        if (affected == 0) {
            log.warn("Topup {} already processed or not PENDING, skipping", topup.getOrderCode());
            return;
        }

        // Credit wallet balance
        int walletUpdated = walletRepository.creditBalance(topup.getUserId(), topup.getAmount());
        if (walletUpdated == 0) {
            // Wallet doesn't exist yet, create one
            WalletEntity newWallet = WalletEntity.builder()
                    .userId(topup.getUserId())
                    .balance(topup.getAmount())
                    .build();
            walletRepository.save(newWallet);
        }

        walletRepository.findById(topup.getUserId()).ifPresent(w ->
                walletRealtimePublisher.publishWalletUpdate(topup.getUserId(), w.getBalance().doubleValue()));

        log.info("Wallet credited: user={}, amount={}, orderCode={}", topup.getUserId(), topup.getAmount(), topup.getOrderCode());
    }

    // ===================== PUBLIC API METHODS =====================

    @Override
    @Transactional
    public Map<String, Object> createTopup(String userId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(1000)) < 0) {
            throw new BadRequestException("Số tiền nạp tối thiểu là 1.000 VNĐ");
        }

        long orderCode = generateOrderCode();
        String description = String.valueOf(orderCode);
        String verifyUrl = "http://localhost:" + gatewayPort + "/topup/verify/" + orderCode;
        String cancelUrl = verifyUrl;
        String returnUrl = verifyUrl;

        // Create PayOS signature: amount=X&cancelUrl=X&description=X&orderCode=X&returnUrl=X
        String signatureData = "amount=" + amount.intValue()
                + "&cancelUrl=" + cancelUrl
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + returnUrl;
        String signature = createSignature(signatureData);

        // Build PayOS request body
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderCode", orderCode);
        body.put("amount", amount.intValue());
        body.put("description", description);
        body.put("cancelUrl", cancelUrl);
        body.put("returnUrl", returnUrl);
        body.put("signature", signature);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, createPayosHeaders());

        Map payosResponse;
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    payosUrl + "/v2/payment-requests",
                    entity,
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BadRequestException("Tạo link thanh toán thất bại");
            }
            payosResponse = response.getBody();
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Tạo link thanh toán thất bại: " + e.getMessage());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payosResponse.get("data");
        String checkoutUrl = (String) data.get("checkoutUrl");
        String paymentLinkId = (String) data.get("paymentLinkId");

        TopupEntity topup = TopupEntity.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .orderCode(orderCode)
                .amount(amount)
                .status(TopupStatus.PENDING)
                .checkoutUrl(checkoutUrl)
                .paymentLinkId(paymentLinkId)
                .description(description)
                .build();
        topupRepository.save(topup);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkoutUrl", checkoutUrl);
        result.put("orderCode", orderCode);
        return result;
    }

    @Override
    @Transactional
    public String verifyTopup(Long orderCode) {
        TopupEntity topup = topupRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch nạp tiền"));

        return syncTopupFromPayos(topup);
    }

    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public void handleWebhook(Map<String, Object> webhookData) {
        Map<String, Object> data = (Map<String, Object>) webhookData.get("data");
        String signature = (String) webhookData.get("signature");

        if (data == null || data.get("orderCode") == null) return;

        if (signature == null || !verifyWebhookSignature(data, signature)) {
            log.warn("Invalid webhook signature for orderCode={}", data.get("orderCode"));
            return;
        }

        Long orderCode = Long.valueOf(String.valueOf(data.get("orderCode")));
        Optional<TopupEntity> topupOpt = topupRepository.findByOrderCode(orderCode);
        if (topupOpt.isEmpty()) {
            log.warn("Topup not found for webhook orderCode={}", orderCode);
            return;
        }

        TopupEntity topup = topupOpt.get();
        if (topup.getStatus() == TopupStatus.PAID) {
            log.info("Topup {} already PAID, webhook idempotency skip", orderCode);
            return;
        }

        // Determine if payment is successful
        String webhookCode = String.valueOf(webhookData.getOrDefault("code", data.getOrDefault("code", "")));
        String webhookStatus = String.valueOf(data.getOrDefault("status", "")).toUpperCase();
        boolean isPaymentSuccess = "00".equals(webhookCode) || "PAID".equals(webhookStatus);

        if (!isPaymentSuccess) {
            log.info("Webhook ignored for orderCode={}: code={}, status={}", orderCode,
                    webhookCode.isEmpty() ? "N/A" : webhookCode,
                    webhookStatus.isEmpty() ? "N/A" : webhookStatus);
            return;
        }

        // Verify amount match
        BigDecimal webhookAmount = new BigDecimal(String.valueOf(data.get("amount")));
        if (webhookAmount.compareTo(topup.getAmount()) != 0) {
            log.error("Webhook amount mismatch: webhook={}, local={}, orderCode={}", webhookAmount, topup.getAmount(), orderCode);
            return;
        }

        // Credit wallet with transaction details from webhook
        Map<String, Object> transaction = new HashMap<>();
        transaction.put("reference", data.get("reference"));
        transaction.put("counterAccountBankName", data.get("counterAccountBankName"));
        transaction.put("counterAccountName", data.get("counterAccountName"));
        transaction.put("counterAccountNumber", data.get("counterAccountNumber"));
        transaction.put("transactionDateTime", data.get("transactionDateTime"));

        creditWallet(topup, transaction);
    }

    @Override
    @Transactional
    public List<TopupEntity> getTopupHistory(String userId) {
        List<TopupEntity> history = topupRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<TopupEntity> pendingTopups = history.stream()
                .filter(t -> t.getStatus() == TopupStatus.PENDING)
                .collect(Collectors.toList());

        if (pendingTopups.isEmpty()) return history;

        boolean hasChanged = false;
        for (TopupEntity topup : pendingTopups) {
            try {
                String result = syncTopupFromPayos(topup);
                if (!"PENDING".equals(result)) hasChanged = true;
            } catch (Exception e) {
                log.warn("Skip topup sync orderCode={}: {}", topup.getOrderCode(), e.getMessage());
            }
        }

        if (!hasChanged) return history;

        // Re-fetch after sync
        return topupRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional
    public void cancelTopup(Long orderCode, String userId) {
        TopupEntity topup = topupRepository.findByOrderCodeAndUserId(orderCode, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch nạp tiền"));

        if (topup.getStatus() != TopupStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể huỷ giao dịch đang chờ thanh toán");
        }

        // Call PayOS to cancel the payment link
        Map<String, Object> cancelBody = Map.of("cancellationReason", "Người dùng huỷ giao dịch");
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(cancelBody, createPayosHeaders());

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    payosUrl + "/v2/payment-requests/" + orderCode + "/cancel",
                    entity,
                    Map.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("PayOS cancel API returned non-2xx");
            }
        } catch (Exception e) {
            log.warn("payOS cancel failed: {}, orderCode={}", e.getMessage(), orderCode);

            // Fallback: sync with PayOS to determine actual status
            String latestStatus = syncTopupFromPayos(topup);
            if ("CANCELLED".equals(latestStatus) || "EXPIRED".equals(latestStatus)) {
                return;
            }
            if ("PAID".equals(latestStatus)) {
                throw new BadRequestException("Giao dịch đã thanh toán thành công, không thể huỷ");
            }
            throw new BadRequestException("Không thể huỷ giao dịch trên payOS");
        }

        // Update local status
        int affected = topupRepository.updateStatusByOrderCode(orderCode, TopupStatus.PENDING, TopupStatus.CANCELLED);
        if (affected == 0) {
            throw new BadRequestException("Giao dịch đã được xử lý, không thể huỷ");
        }
    }
}
