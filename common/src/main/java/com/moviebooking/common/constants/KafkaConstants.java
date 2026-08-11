package com.moviebooking.common.constants;

public final class KafkaConstants {
    private KafkaConstants() {}

    public static final String HEADER_EVENT_TYPE = "eventType";
    public static final String HEADER_EVENT_ID = "id";

    public static final String GROUP_SEAT_SERVICE = "seat-service-group";
    public static final String GROUP_BOOKING_SERVICE = "booking-service-group";
    public static final String GROUP_PAYMENT_SERVICE = "payment-service-group";
    public static final String GROUP_AI_RECOMMENDER_SERVICE = "ai-recommender-service-group";
}
