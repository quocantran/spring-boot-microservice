package com.moviebooking.common.constants;

public final class SseConstants {
    private SseConstants() {}

    public static final String EVENT_SEAT_UPDATE = "seat-update";
    public static final String EVENT_BOOKING_UPDATE = "booking-update";
    public static final String EVENT_WALLET_UPDATE = "wallet-update";
    public static final String EVENT_CONNECTED = "connected";
    public static final long DEFAULT_SSE_TIMEOUT_MS = 300_000L; // 5 minutes
}
