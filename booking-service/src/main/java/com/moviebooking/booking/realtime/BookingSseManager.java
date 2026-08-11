package com.moviebooking.booking.realtime;

import com.moviebooking.common.constants.SseConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Manages SSE emitters for booking status updates.
 * Safe against null keys, supports null/empty fallbacks.
 */
@Slf4j
@Component
public class BookingSseManager {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByBookingId = new ConcurrentHashMap<>();
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    public SseEmitter createBookingEmitter(String bookingId) {
        String key = (bookingId != null && !bookingId.trim().isEmpty()) ? bookingId : "unknown";
        SseEmitter emitter = new SseEmitter(SseConstants.DEFAULT_SSE_TIMEOUT_MS);
        emittersByBookingId.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> removeBookingEmitter(key, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        sendConnectedEvent(emitter);
        log.debug("SSE emitter added for bookingId key: {}", key);
        return emitter;
    }

    public SseEmitter createUserEmitter(String userId) {
        String key = (userId != null && !userId.trim().isEmpty()) ? userId : "global";
        SseEmitter emitter = new SseEmitter(SseConstants.DEFAULT_SSE_TIMEOUT_MS);
        emittersByUserId.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> removeUserEmitter(key, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        sendConnectedEvent(emitter);
        log.debug("SSE emitter added for userId key: {}", key);
        return emitter;
    }

    private void removeBookingEmitter(String bookingId, SseEmitter emitter) {
        if (bookingId == null) return;
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByBookingId.get(bookingId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByBookingId.remove(bookingId);
            }
        }
    }

    private void removeUserEmitter(String userId, SseEmitter emitter) {
        if (userId == null) return;
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByUserId.remove(userId);
            }
        }
    }

    private void sendConnectedEvent(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event()
                    .name(SseConstants.EVENT_CONNECTED)
                    .data("{\"status\":\"CONNECTED\"}"));
        } catch (IOException ignored) {
        }
    }

    public void broadcastBookingUpdate(String bookingId, String userId, String jsonData) {
        // Send to booking-specific listeners
        if (bookingId != null && !bookingId.trim().isEmpty()) {
            sendToGroup(emittersByBookingId.get(bookingId), jsonData, emitter -> removeBookingEmitter(bookingId, emitter));
        }

        // Send to user-specific listeners
        if (userId != null && !userId.trim().isEmpty()) {
            sendToGroup(emittersByUserId.get(userId), jsonData, emitter -> removeUserEmitter(userId, emitter));
        }

        // Send to global listeners
        sendToGroup(emittersByUserId.get("global"), jsonData, emitter -> removeUserEmitter("global", emitter));
    }

    private void sendToGroup(CopyOnWriteArrayList<SseEmitter> emitters, String jsonData, Consumer<SseEmitter> removeAction) {
        if (emitters == null || emitters.isEmpty()) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(SseConstants.EVENT_BOOKING_UPDATE)
                        .data(jsonData));
            } catch (IOException | IllegalStateException e) {
                dead.add(emitter);
            }
        }
        dead.forEach(removeAction);
    }
}
