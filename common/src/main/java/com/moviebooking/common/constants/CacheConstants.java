package com.moviebooking.common.constants;

public final class CacheConstants {
    private CacheConstants() {}

    public static final String CACHE_NAME_MOVIES = "movies";
    public static final String CACHE_NAME_SHOWTIMES = "showtimes";
    public static final String CACHE_NULL_SENTINEL = "__CACHE_NULL__";
    public static final String KEY_MOVIES_ALL = "movies::all";
    public static final String PREFIX_MOVIES_CACHE = "movies::";
    public static final String PREFIX_SHOWTIMES_CACHE = "showtimes::";
    public static final String LOCK_CACHE_MOVIES_ALL = "lock:cache:movies:all";
    public static final String LOCK_CACHE_MOVIES_PREFIX = "lock:cache:movies:";
    public static final String LOCK_CACHE_SHOWTIMES_PREFIX = "lock:cache:showtimes:";

    public static final long DEFAULT_CACHE_LOCK_TTL_MS = 3000L;
    public static final long DEFAULT_CACHE_LOCK_RETRY_DELAY_MS = 100L;
    public static final long TTL_JITTER_MAX_SECONDS = 120L;
}
