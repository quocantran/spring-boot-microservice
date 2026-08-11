package com.moviebooking.common.constants;

public final class RedisConstants {
    private RedisConstants() {}

    public static final String CHANNEL_SEAT_UPDATES_PREFIX = "seat-updates:";
    public static final String PATTERN_SEAT_UPDATES = "seat-updates:*";
    public static final String CHANNEL_BOOKING_UPDATES_PREFIX = "booking-updates:";
    public static final String PATTERN_BOOKING_UPDATES = "booking-updates:*";
    public static final String CHANNEL_WALLET_UPDATES_PREFIX = "wallet-updates:";
    public static final String PATTERN_WALLET_UPDATES = "wallet-updates:*";
    public static final String LOCK_SEAT_PREFIX = "lock:seat:";
    public static final long DEFAULT_SEAT_LOCK_TTL_MS = 5000L;
}
