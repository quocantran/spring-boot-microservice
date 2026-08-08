package com.moviebooking.recommender.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.event.EventPayloads.BookingConfirmedPayload;
import com.moviebooking.common.event.EventPayloads.MovieCreatedPayload;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.event.EventTypes.Topics;
import com.moviebooking.common.idempotency.IdempotencyService;
import com.moviebooking.recommender.service.RecommenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecommenderKafkaConsumer {

    private final RecommenderService recommenderService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    /**
     * Listen to movie.events for MOVIE_CREATED → generate embedding
     */
    @KafkaListener(topics = Topics.MOVIE_EVENTS, groupId = "ai-recommender-service-group")
    public void handleMovieEvents(ConsumerRecord<String, String> record) {
        handleMessage(record);
    }

    /**
     * Listen to booking.events for BOOKING_CONFIRMED → save user behavior
     */
    @KafkaListener(topics = Topics.BOOKING_EVENTS, groupId = "ai-recommender-service-group")
    public void handleBookingEvents(ConsumerRecord<String, String> record) {
        handleMessage(record);
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(ConsumerRecord<String, String> record) {
        try {
            String headerEventType = extractHeader(record, "eventType");
            String headerEventId = extractHeader(record, "id");

            String rawValue = record.value();
            if (rawValue == null || rawValue.isEmpty()) return;

            Map<String, Object> mapValue = objectMapper.readValue(rawValue, Map.class);

            String eventType = headerEventType != null ? headerEventType : (String) mapValue.get("eventType");
            String eventId = headerEventId != null ? headerEventId : (String) mapValue.get("id");

            if (eventType == null || eventId == null) return;

            // Only process MOVIE_CREATED and BOOKING_CONFIRMED
            if (!Events.MOVIE_CREATED.equals(eventType) && !Events.BOOKING_CONFIRMED.equals(eventType)) {
                return;
            }

            Object payloadObj = mapValue.containsKey("payload") ? mapValue.get("payload") : mapValue;
            String payloadJson = objectMapper.writeValueAsString(payloadObj);

            idempotencyService.processWithIdempotency(eventId, eventType, () -> {
                try {
                    if (Events.MOVIE_CREATED.equals(eventType)) {
                        MovieCreatedPayload payload = objectMapper.readValue(payloadJson, MovieCreatedPayload.class);
                        recommenderService.generateAndSaveEmbedding(
                                payload.getMovieId(),
                                payload.getTitle(),
                                payload.getGenre(),
                                payload.getDescription()
                        );
                        log.info("Processed MOVIE_CREATED: movieId={}, title={}", payload.getMovieId(), payload.getTitle());
                    } else if (Events.BOOKING_CONFIRMED.equals(eventType)) {
                        BookingConfirmedPayload payload = objectMapper.readValue(payloadJson, BookingConfirmedPayload.class);
                        if (payload.getUserId() != null) {
                            recommenderService.saveUserBehavior(payload.getUserId(), payload.getMovieId());
                            log.info("Processed BOOKING_CONFIRMED: userId={}, movieId={}", payload.getUserId(), payload.getMovieId());
                        }
                    }
                } catch (Exception e) {
                    log.error("Failed to process event: {}", eventType, e);
                    throw new RuntimeException("Error handling event: " + eventType, e);
                }
            });

        } catch (Exception e) {
            log.error("Error consuming record from Kafka", e);
        }
    }

    private String extractHeader(ConsumerRecord<String, String> record, String headerName) {
        Header header = record.headers().lastHeader(headerName);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return null;
    }
}
