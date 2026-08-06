package com.moviebooking.common.event;

public final class EventTypes {

    private EventTypes() {}

    public static final class AggregateTypes {
        private AggregateTypes() {}
        public static final String BOOKING = "booking";
        public static final String SEAT = "seat";
        public static final String PAYMENT = "payment";
        public static final String MOVIE = "movie";
    }

    public static final class Events {
        private Events() {}
        public static final String BOOKING_CREATED = "BOOKING_CREATED";
        public static final String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
        public static final String BOOKING_CANCELLED = "BOOKING_CANCELLED";

        public static final String SEATS_RESERVED = "SEATS_RESERVED";
        public static final String SEAT_RESERVATION_FAILED = "SEAT_RESERVATION_FAILED";
        public static final String SEATS_COMPENSATED = "SEATS_COMPENSATED";

        public static final String PAYMENT_PROCESSED = "PAYMENT_PROCESSED";
        public static final String PAYMENT_FAILED = "PAYMENT_FAILED";

        public static final String MOVIE_CREATED = "MOVIE_CREATED";
    }

    public static final class Topics {
        private Topics() {}
        public static final String BOOKING_EVENTS = "booking.events";
        public static final String SEAT_EVENTS = "seat.events";
        public static final String PAYMENT_EVENTS = "payment.events";
        public static final String MOVIE_EVENTS = "movie.events";
    }
}
