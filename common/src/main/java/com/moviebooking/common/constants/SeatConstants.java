package com.moviebooking.common.constants;

public final class SeatConstants {
    private SeatConstants() {}

    public static final String SEAT_ID_PREFIX = "seat-";
    public static final String STATUS_BOOKED = "BOOKED";
    public static final String STATUS_HELD = "HELD";
    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final long SEAT_HOLD_MINUTES = 5L;
    public static final int DEFAULT_ROWS = 5;
    public static final int DEFAULT_COLS = 8;
}
