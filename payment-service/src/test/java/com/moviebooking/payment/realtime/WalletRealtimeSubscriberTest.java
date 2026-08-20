package com.moviebooking.payment.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletRealtimeSubscriberTest {

    @Mock
    private WalletSseManager walletSseManager;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private WalletRealtimeSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new WalletRealtimeSubscriber(walletSseManager, objectMapper);
    }

    @Test
    @DisplayName("Case 1: Should parse userId and forward message to WalletSseManager")
    void should_ForwardToSSE_when_ValidMessageReceived() {
        String body = "{\"userId\":\"u-123\",\"balance\":500000.0}";
        Message message = new DefaultMessage("wallet:updates:u-123".getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(message, null);

        verify(walletSseManager).broadcastWalletUpdate(eq("u-123"), eq(body));
    }

    @Test
    @DisplayName("Case 2: Should broadcast with null userId when message lacks userId property")
    void should_HandleMissingUserId_when_MessageLacksUserId() {
        String body = "{\"balance\":500000.0}";
        Message message = new DefaultMessage("wallet:updates".getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(message, null);

        verify(walletSseManager).broadcastWalletUpdate(isNull(), eq(body));
    }

    @Test
    @DisplayName("Case 3: Should not crash when message body is invalid JSON")
    void should_NotCrash_when_MessageBodyIsInvalidJson() {
        String body = "invalid-json-string";
        Message message = new DefaultMessage("channel".getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));

        // Should not throw exception
        subscriber.onMessage(message, null);

        verifyNoInteractions(walletSseManager);
    }
}
