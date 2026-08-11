package com.moviebooking.booking.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.booking.entity.BookingEntity;
import com.moviebooking.common.constants.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes booking status changes to Redis Pub/Sub so that all instances
 * of booking-service can broadcast SSE events to connected clients in real-time.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingRealtimePublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final BookingSseManager bookingSseManager;

    public void publishBookingUpdate(BookingEntity booking) {
        if (booking == null) return;
        try {
            String json = objectMapper.writeValueAsString(booking);

            // Broadcast directly to local SSE listeners
            bookingSseManager.broadcastBookingUpdate(booking.getId(), booking.getUserId(), json);

            // Also publish to Redis Pub/Sub for multi-instance scaling
            String channel = RedisConstants.CHANNEL_BOOKING_UPDATES_PREFIX + booking.getId();
            stringRedisTemplate.convertAndSend(channel, json);
            log.info("Published booking update to channel {}: status={}", channel, booking.getStatus());
        } catch (Exception e) {
            log.error("Failed to publish booking update for bookingId: {}", booking.getId(), e);
        }
    }
}
