package com.moviebooking.payment.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.constants.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Redis Pub/Sub wallet update channels and forwards to local SSE emitters.
 */
@Slf4j
@Component
public class WalletRealtimeSubscriber implements MessageListener {

    private final WalletSseManager walletSseManager;
    private final ObjectMapper objectMapper;

    public WalletRealtimeSubscriber(WalletSseManager walletSseManager, ObjectMapper objectMapper) {
        this.walletSseManager = walletSseManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            JsonNode node = objectMapper.readTree(body);
            String userId = node.has("userId") ? node.get("userId").asText() : null;
            walletSseManager.broadcastWalletUpdate(userId, body);
        } catch (Exception e) {
            log.error("Error processing Redis Pub/Sub wallet message", e);
        }
    }

    @Configuration
    static class RedisSubscriberConfig {

        @Bean
        public RedisMessageListenerContainer walletUpdateListenerContainer(
                RedisConnectionFactory connectionFactory,
                WalletRealtimeSubscriber subscriber) {

            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.addMessageListener(subscriber, new PatternTopic(RedisConstants.PATTERN_WALLET_UPDATES));

            log.info("Redis Pub/Sub listener registered for pattern: {}", RedisConstants.PATTERN_WALLET_UPDATES);
            return container;
        }
    }
}
