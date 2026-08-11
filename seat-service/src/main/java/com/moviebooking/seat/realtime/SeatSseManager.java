package com.moviebooking.seat.realtime;

import com.moviebooking.common.constants.SseConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages SSE emitters grouped by showtimeId.
 * Thread-safe — supports concurrent registration, removal, and broadcasting.
 */
@Slf4j
@Component
public class SeatSseManager {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByShowtime = new ConcurrentHashMap<>();

    /**
     * Register a new SSE emitter for a given showtime.
     */
    public void addEmitter(String showtimeId, SseEmitter emitter) {
        emittersByShowtime.computeIfAbsent(showtimeId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Auto-cleanup on completion, timeout, or error
        Runnable cleanup = () -> removeEmitter(showtimeId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        log.debug("SSE emitter added for showtimeId: {}, total: {}",
                showtimeId, emittersByShowtime.getOrDefault(showtimeId, new CopyOnWriteArrayList<>()).size());
    }

    /**
     * Remove a specific emitter from a showtime's list.
     */
    public void removeEmitter(String showtimeId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByShowtime.get(showtimeId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                emittersByShowtime.remove(showtimeId);
            }
        }
    }

    /**
     * Broadcast a seat update event to all connected clients watching a showtime.
     */
    public void broadcast(String showtimeId, String jsonData) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByShowtime.get(showtimeId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(SseConstants.EVENT_SEAT_UPDATE)
                        .data(jsonData));
            } catch (IOException | IllegalStateException e) {
                deadEmitters.add(emitter);
            }
        }

        // Cleanup dead emitters
        deadEmitters.forEach(emitter -> removeEmitter(showtimeId, emitter));

        if (!deadEmitters.isEmpty()) {
            log.debug("Cleaned up {} dead SSE emitters for showtimeId: {}", deadEmitters.size(), showtimeId);
        }
    }
}

