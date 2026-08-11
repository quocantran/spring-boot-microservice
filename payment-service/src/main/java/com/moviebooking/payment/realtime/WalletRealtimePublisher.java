package com.moviebooking.payment.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.constants.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes wallet balance updates to Redis Pub/Sub and local SSE clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletRealtimePublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final WalletSseManager walletSseManager;

    public void publishWalletUpdate(String userId, Double balance) {
        if (userId == null) return;
        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("userId", userId);
            data.put("balance", balance);

            String json = objectMapper.writeValueAsString(data);

            // Broadcast to local SSE emitters
            walletSseManager.broadcastWalletUpdate(userId, json);

            // Publish to Redis Pub/Sub for multi-instance scaling
            String channel = RedisConstants.CHANNEL_WALLET_UPDATES_PREFIX + userId;
            stringRedisTemplate.convertAndSend(channel, json);
            log.info("Published wallet update to channel {}: balance={}", channel, balance);
        } catch (Exception e) {
            log.error("Failed to publish wallet update for userId: {}", userId, e);
        }
    }
}
