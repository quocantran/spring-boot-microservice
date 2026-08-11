package com.moviebooking.payment.realtime;

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
 * Manages SSE emitters for realtime wallet balance updates per user.
 */
@Slf4j
@Component
public class WalletSseManager {

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    public SseEmitter createUserEmitter(String userId) {
        String key = (userId != null && !userId.trim().isEmpty()) ? userId : "global";
        SseEmitter emitter = new SseEmitter(SseConstants.DEFAULT_SSE_TIMEOUT_MS);
        emittersByUserId.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable cleanup = () -> removeUserEmitter(key, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        sendConnectedEvent(emitter);
        log.debug("SSE emitter added for wallet userId key: {}", key);
        return emitter;
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

    public void broadcastWalletUpdate(String userId, String jsonData) {
        if (userId != null && !userId.trim().isEmpty()) {
            sendToGroup(emittersByUserId.get(userId), jsonData, emitter -> removeUserEmitter(userId, emitter));
        }
        sendToGroup(emittersByUserId.get("global"), jsonData, emitter -> removeUserEmitter("global", emitter));
    }

    private void sendToGroup(CopyOnWriteArrayList<SseEmitter> emitters, String jsonData, Consumer<SseEmitter> removeAction) {
        if (emitters == null || emitters.isEmpty()) return;
        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(SseConstants.EVENT_WALLET_UPDATE)
                        .data(jsonData));
            } catch (IOException | IllegalStateException e) {
                dead.add(emitter);
            }
        }
        dead.forEach(removeAction);
    }
}
