package com.moviebooking.common.redis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockService {

    private final StringRedisTemplate redisTemplate;

    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    private final DefaultRedisScript<Long> releaseRedisScript = new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LockResult {
        private boolean success;
        private Map<String, String> tokens;
        private String failedKey;
    }

    public String acquireLock(String key, long ttlMs) {
        String token = UUID.randomUUID().toString();
        Boolean success = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
            Object obj = connection.execute(
                    "SET",
                    key.getBytes(StandardCharsets.UTF_8),
                    token.getBytes(StandardCharsets.UTF_8),
                    "PX".getBytes(StandardCharsets.UTF_8),
                    String.valueOf(ttlMs).getBytes(StandardCharsets.UTF_8),
                    "NX".getBytes(StandardCharsets.UTF_8)
            );
            return obj != null;
        });

        return Boolean.TRUE.equals(success) ? token : null;
    }

    public boolean releaseLock(String key, String token) {
        Long result = redisTemplate.execute(releaseRedisScript, Collections.singletonList(key), token);
        return Long.valueOf(1L).equals(result);
    }

    public LockResult acquireMultipleLocks(List<String> keys, long ttlMs) {
        Map<String, String> tokens = new HashMap<>();
        List<String> tokenValues = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            tokenValues.add(UUID.randomUUID().toString());
        }

        List<Object> results = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (int i = 0; i < keys.size(); i++) {
                connection.execute(
                        "SET",
                        keys.get(i).getBytes(StandardCharsets.UTF_8),
                        tokenValues.get(i).getBytes(StandardCharsets.UTF_8),
                        "PX".getBytes(StandardCharsets.UTF_8),
                        String.valueOf(ttlMs).getBytes(StandardCharsets.UTF_8),
                        "NX".getBytes(StandardCharsets.UTF_8)
                );
            }
            return null;
        });

        List<Integer> failedIndices = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            Object res = results.get(i);
            if (res != null) {
                tokens.put(keys.get(i), tokenValues.get(i));
            } else {
                failedIndices.add(i);
            }
        }

        if (!failedIndices.isEmpty()) {
            releaseMultipleLocks(tokens);
            return LockResult.builder()
                    .success(false)
                    .tokens(new HashMap<>())
                    .failedKey(keys.get(failedIndices.get(0)))
                    .build();
        }

        return LockResult.builder()
                .success(true)
                .tokens(tokens)
                .build();
    }

    public void releaseMultipleLocks(Map<String, String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }

        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, String> entry : tokens.entrySet()) {
                connection.eval(
                        RELEASE_SCRIPT.getBytes(StandardCharsets.UTF_8),
                        org.springframework.data.redis.connection.ReturnType.INTEGER,
                        1,
                        entry.getKey().getBytes(StandardCharsets.UTF_8),
                        entry.getValue().getBytes(StandardCharsets.UTF_8)
                );
            }
            return null;
        });
    }
}
