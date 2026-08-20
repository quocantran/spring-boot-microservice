package com.moviebooking.payment.realtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WalletSseManagerTest {

    private WalletSseManager sseManager;

    @BeforeEach
    void setUp() {
        sseManager = new WalletSseManager();
    }

    @Test
    @DisplayName("Case 1: Should create emitter for specific user and store in map")
    void should_CreateEmitterForUser() {
        SseEmitter emitter = sseManager.createUserEmitter("user-1");

        assertThat(emitter).isNotNull();
        Map<String, CopyOnWriteArrayList<SseEmitter>> map =
                (Map<String, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(sseManager, "emittersByUserId");
        assertThat(map).containsKey("user-1");
        assertThat(map.get("user-1")).contains(emitter);
    }

    @Test
    @DisplayName("Case 2: Should use fallback 'global' key when userId is null or empty")
    void should_UseFallbackGlobalKey_when_UserIdIsNull() {
        SseEmitter emitterNull = sseManager.createUserEmitter(null);
        SseEmitter emitterEmpty = sseManager.createUserEmitter("   ");

        Map<String, CopyOnWriteArrayList<SseEmitter>> map =
                (Map<String, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(sseManager, "emittersByUserId");
        assertThat(map).containsKey("global");
        assertThat(map.get("global")).contains(emitterNull, emitterEmpty);
    }

    @Test
    @DisplayName("Case 3: Should broadcast to correct user emitter")
    void should_BroadcastToCorrectUser_when_CalledWithUserId() throws IOException {
        SseEmitter mockEmitterUser1 = mock(SseEmitter.class);
        SseEmitter mockEmitterUser2 = mock(SseEmitter.class);

        Map<String, CopyOnWriteArrayList<SseEmitter>> map =
                (Map<String, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(sseManager, "emittersByUserId");

        map.computeIfAbsent("user-1", k -> new CopyOnWriteArrayList<>()).add(mockEmitterUser1);
        map.computeIfAbsent("user-2", k -> new CopyOnWriteArrayList<>()).add(mockEmitterUser2);

        sseManager.broadcastWalletUpdate("user-1", "{\"balance\":200000}");

        verify(mockEmitterUser1).send(any(SseEmitter.SseEventBuilder.class));
        verify(mockEmitterUser2, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("Case 4: Should always broadcast to global emitters regardless of userId")
    void should_BroadcastToGlobalEmitters_always() throws IOException {
        SseEmitter mockGlobalEmitter = mock(SseEmitter.class);
        Map<String, CopyOnWriteArrayList<SseEmitter>> map =
                (Map<String, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(sseManager, "emittersByUserId");
        map.computeIfAbsent("global", k -> new CopyOnWriteArrayList<>()).add(mockGlobalEmitter);

        sseManager.broadcastWalletUpdate("any-user", "{\"balance\":300000}");

        verify(mockGlobalEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("Case 5: Should remove dead emitters when send throws IOException")
    void should_RemoveDeadEmitters_when_SendFails() throws IOException {
        SseEmitter deadEmitter = mock(SseEmitter.class);
        doThrow(new IOException("Connection reset by peer")).when(deadEmitter).send(any(SseEmitter.SseEventBuilder.class));

        Map<String, CopyOnWriteArrayList<SseEmitter>> map =
                (Map<String, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(sseManager, "emittersByUserId");
        map.computeIfAbsent("user-dead", k -> new CopyOnWriteArrayList<>()).add(deadEmitter);

        sseManager.broadcastWalletUpdate("user-dead", "{\"balance\":100000}");

        // Dead emitter should be removed
        assertThat(map.get("user-dead")).isNullOrEmpty();
    }

    @Test
    @DisplayName("Case 6: Should remove emitter and clean up map entry when removeUserEmitter is called")
    void should_RemoveEmitter_when_RemoveUserEmitterCalled() {
        SseEmitter emitter = sseManager.createUserEmitter("user-completed");
        Map<String, CopyOnWriteArrayList<SseEmitter>> map =
                (Map<String, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(sseManager, "emittersByUserId");

        assertThat(map.get("user-completed")).contains(emitter);

        ReflectionTestUtils.invokeMethod(sseManager, "removeUserEmitter", "user-completed", emitter);

        assertThat(map.get("user-completed")).isNullOrEmpty();
    }

    @Test
    @DisplayName("Case 7: Should handle removeUserEmitter with null userId gracefully")
    void should_HandleRemoveUserEmitter_when_UserIdIsNull() {
        SseEmitter emitter = sseManager.createUserEmitter("user-null-test");

        ReflectionTestUtils.invokeMethod(sseManager, "removeUserEmitter", (String) null, emitter);

        Map<String, CopyOnWriteArrayList<SseEmitter>> map =
                (Map<String, CopyOnWriteArrayList<SseEmitter>>) ReflectionTestUtils.getField(sseManager, "emittersByUserId");
        assertThat(map.get("user-null-test")).contains(emitter);
    }
}
