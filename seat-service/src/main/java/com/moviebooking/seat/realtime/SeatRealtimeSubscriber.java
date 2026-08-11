package com.moviebooking.seat.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.constants.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Subscribes to Redis Pub/Sub seat update channels and forwards messages
 * to local SSE emitters. This ensures cross-pod real-time updates work
 * when seat-service is scaled to multiple instances.
 */
@Slf4j
@Component
public class SeatRealtimeSubscriber implements MessageListener {

    private final SeatSseManager seatSseManager;
    private final ObjectMapper objectMapper;

    public SeatRealtimeSubscriber(SeatSseManager seatSseManager, ObjectMapper objectMapper) {
        this.seatSseManager = seatSseManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String body = new String(message.getBody());

            // Extract showtimeId from channel name
            String showtimeId = channel.replace(RedisConstants.CHANNEL_SEAT_UPDATES_PREFIX, "");

            log.info("Received Redis Pub/Sub message on channel {}: {}", channel, body);

            // Forward the raw JSON to all SSE clients watching this showtime
            seatSseManager.broadcast(showtimeId, body);

        } catch (Exception e) {
            log.error("Error processing Redis Pub/Sub message", e);
        }
    }

    /**
     * Configuration for Redis message listener container.
     * Uses pattern subscription to listen to all seat-updates channels.
     */
    @Configuration
    static class RedisSubscriberConfig {

        @Bean
        public RedisMessageListenerContainer seatUpdateListenerContainer(
                RedisConnectionFactory connectionFactory,
                SeatRealtimeSubscriber subscriber) {

            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.addMessageListener(subscriber, new PatternTopic(RedisConstants.PATTERN_SEAT_UPDATES));

            log.info("Redis Pub/Sub listener registered for pattern: {}", RedisConstants.PATTERN_SEAT_UPDATES);
            return container;
        }
    }
}

