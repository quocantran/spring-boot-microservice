package com.moviebooking.payment.service;

import com.moviebooking.payment.entity.TopupEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface TopupService {

    Map<String, Object> createTopup(String userId, BigDecimal amount);

    String verifyTopup(Long orderCode);

    void handleWebhook(Map<String, Object> webhookData);

    List<TopupEntity> getTopupHistory(String userId);

    void cancelTopup(Long orderCode, String userId);
}
