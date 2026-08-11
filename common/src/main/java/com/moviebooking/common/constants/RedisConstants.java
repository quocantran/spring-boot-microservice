package com.moviebooking.common.constants;

public final class RedisConstants {
    private RedisConstants() {}

    public static final String CHANNEL_SEAT_UPDATES_PREFIX = "seat-updates:";
    public static final String PATTERN_SEAT_UPDATES = "seat-updates:*";
    public static final String LOCK_SEAT_PREFIX = "lock:seat:";
    public static final long DEFAULT_SEAT_LOCK_TTL_MS = 5000L;
}
