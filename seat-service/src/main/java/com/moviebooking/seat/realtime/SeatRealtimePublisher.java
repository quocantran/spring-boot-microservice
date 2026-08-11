package com.moviebooking.seat.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.constants.RedisConstants;
import com.moviebooking.common.constants.SeatConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Publishes seat status changes to Redis Pub/Sub so that all instances
 * of seat-service (across pods) can broadcast SSE events to connected clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatRealtimePublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public void publishSeatsBooked(String showtimeId, List<String> seatIds, List<String> seatNumbers) {
        try {
            SeatUpdateMessage message = SeatUpdateMessage.builder()
                    .showtimeId(showtimeId)
                    .seatIds(seatIds)
                    .seatNumbers(seatNumbers)
                    .status(SeatConstants.STATUS_BOOKED)
                    .build();

            String json = objectMapper.writeValueAsString(message);
            String channel = RedisConstants.CHANNEL_SEAT_UPDATES_PREFIX + showtimeId;

            stringRedisTemplate.convertAndSend(channel, json);
            log.info("Published seat update to channel {}: {} seats booked", channel, seatIds.size());
        } catch (Exception e) {
            log.error("Failed to publish seat update for showtimeId: {}", showtimeId, e);
        }
    }
}
