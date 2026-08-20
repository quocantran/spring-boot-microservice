package com.moviebooking.payment.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.constants.RedisConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletRealtimePublisherTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private WalletSseManager walletSseManager;

    private WalletRealtimePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new WalletRealtimePublisher(stringRedisTemplate, objectMapper, walletSseManager);
    }

    @Test
    @DisplayName("Case 1: Should broadcast to SSE and publish to Redis when userId is valid")
    void should_BroadcastToSSEAndRedis_when_UserIdIsValid() {
        String userId = "user-realtime-1";
        Double balance = 500000.0;

        publisher.publishWalletUpdate(userId, balance);

        String expectedChannel = RedisConstants.CHANNEL_WALLET_UPDATES_PREFIX + userId;
        verify(walletSseManager).broadcastWalletUpdate(eq(userId), contains("\"userId\":\"user-realtime-1\""));
        verify(stringRedisTemplate).convertAndSend(eq(expectedChannel), contains("\"balance\":500000.0"));
    }

    @Test
    @DisplayName("Case 2: Should do nothing when userId is null")
    void should_DoNothing_when_UserIdIsNull() {
        publisher.publishWalletUpdate(null, 100000.0);

        verifyNoInteractions(walletSseManager);
        verifyNoInteractions(stringRedisTemplate);
    }

    @Test
    @DisplayName("Case 3: Should not crash when Redis publish throws exception")
    void should_NotCrash_when_RedisPublishFails() {
        String userId = "user-redis-down";
        when(stringRedisTemplate.convertAndSend(anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        // Should not throw exception
        publisher.publishWalletUpdate(userId, 200000.0);

        verify(walletSseManager).broadcastWalletUpdate(eq(userId), anyString());
    }
}
