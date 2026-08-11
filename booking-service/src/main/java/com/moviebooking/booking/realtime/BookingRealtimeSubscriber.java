package com.moviebooking.booking.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.booking.entity.BookingEntity;
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
 * Subscribes to Redis Pub/Sub booking update channels and forwards messages
 * to local SSE emitters.
 */
@Slf4j
@Component
public class BookingRealtimeSubscriber implements MessageListener {

    private final BookingSseManager bookingSseManager;
    private final ObjectMapper objectMapper;

    public BookingRealtimeSubscriber(BookingSseManager bookingSseManager, ObjectMapper objectMapper) {
        this.bookingSseManager = bookingSseManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = new String(message.getBody());
            BookingEntity booking = objectMapper.readValue(body, BookingEntity.class);
            bookingSseManager.broadcastBookingUpdate(booking.getId(), booking.getUserId(), body);
        } catch (Exception e) {
            log.error("Error processing Redis Pub/Sub booking message", e);
        }
    }

    @Configuration
    static class RedisSubscriberConfig {

        @Bean
        public RedisMessageListenerContainer bookingUpdateListenerContainer(
                RedisConnectionFactory connectionFactory,
                BookingRealtimeSubscriber subscriber) {

            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            container.addMessageListener(subscriber, new PatternTopic(RedisConstants.PATTERN_BOOKING_UPDATES));

            log.info("Redis Pub/Sub listener registered for pattern: {}", RedisConstants.PATTERN_BOOKING_UPDATES);
            return container;
        }
    }
}
