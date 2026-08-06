package com.moviebooking.common.event;

import lombok.*;

import java.util.List;

public final class EventPayloads {

    private EventPayloads() {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BookingCreatedPayload {
        private String bookingId;
        private String userId;
        private String movieId;
        private String showtimeId;
        private List<String> seatIds;
        private Double totalAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeatsReservedPayload {
        private String bookingId;
        private String userId;
        private String showtimeId;
        private List<String> seatIds;
        private Double totalAmount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeatReservationFailedPayload {
        private String bookingId;
        private String showtimeId;
        private List<String> seatIds;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentProcessedPayload {
        private String bookingId;
        private String paymentId;
        private Double amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentFailedPayload {
        private String bookingId;
        private String showtimeId;
        private List<String> seatIds;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SeatsCompensatedPayload {
        private String bookingId;
        private String showtimeId;
        private List<String> seatIds;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BookingConfirmedPayload {
        private String bookingId;
        private String userId;
        private String movieId;
        private String showtimeId;
        private List<String> seatIds;
        @Builder.Default
        private String status = "CONFIRMED";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MovieCreatedPayload {
        private String movieId;
        private String title;
        private String genre;
        private String description;
        private Integer duration;
        private String posterUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BookingCancelledPayload {
        private String bookingId;
        private String reason;
        @Builder.Default
        private String status = "CANCELLED";
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DebeziumOutboxEvent {
        private String id;
        private String eventType;
        private String aggregateId;
        private String aggregateType;
        private Object payload;
        private String timestamp;
    }
}
