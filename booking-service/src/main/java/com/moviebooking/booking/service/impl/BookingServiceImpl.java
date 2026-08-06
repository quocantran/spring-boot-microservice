package com.moviebooking.booking.service.impl;

import com.moviebooking.booking.dto.CreateBookingRequest;
import com.moviebooking.booking.entity.BookingEntity;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.booking.service.BookingService;
import com.moviebooking.common.event.EventTypes.AggregateTypes;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.event.EventPayloads.*;
import com.moviebooking.common.exception.CustomExceptions.BadRequestException;
import com.moviebooking.common.exception.CustomExceptions.NotFoundException;
import com.moviebooking.common.outbox.OutboxService;
import com.moviebooking.common.outbox.OutboxService.OutboxEventData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final OutboxService outboxService;

    @Override
    @Transactional
    public BookingEntity createBooking(String userId, CreateBookingRequest request) {
        if (request.getSeatIds() == null || request.getSeatIds().isEmpty()) {
            throw new BadRequestException("Vui lòng chọn ít nhất 1 ghế");
        }

        String bookingId = UUID.randomUUID().toString();

        BookingEntity booking = BookingEntity.builder()
                .id(bookingId)
                .userId(userId)
                .movieId(request.getMovieId())
                .showtimeId(request.getShowtimeId())
                .seatIds(request.getSeatIds())
                .totalAmount(request.getTotalAmount())
                .status(BookingStatus.PENDING)
                .build();

        bookingRepository.save(booking);

        BookingCreatedPayload payload = BookingCreatedPayload.builder()
                .bookingId(bookingId)
                .userId(userId)
                .movieId(request.getMovieId())
                .showtimeId(request.getShowtimeId())
                .seatIds(request.getSeatIds())
                .totalAmount(request.getTotalAmount() != null ? request.getTotalAmount().doubleValue() : null)
                .build();

        outboxService.createEvent(OutboxEventData.builder()
                .aggregateType(AggregateTypes.BOOKING)
                .aggregateId(bookingId)
                .eventType(Events.BOOKING_CREATED)
                .payload(payload)
                .build());

        log.info("Booking created: {}, userId: {}", bookingId, userId);
        return booking;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingEntity> getBookingsByUser(String userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingEntity getBookingById(String id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn đặt vé: " + id));
    }

    @Override
    @Transactional
    public void handleSeatsReserved(SeatsReservedPayload payload) {
        log.info("Handling SeatsReserved for bookingId: {}", payload.getBookingId());
        bookingRepository.findById(payload.getBookingId()).ifPresent(booking -> {
            booking.setStatus(BookingStatus.SEATS_RESERVED);
            bookingRepository.save(booking);
        });
    }

    @Override
    @Transactional
    public void handleSeatReservationFailed(SeatReservationFailedPayload payload) {
        log.info("Handling SeatReservationFailed for bookingId: {}, reason: {}", payload.getBookingId(), payload.getReason());
        bookingRepository.findById(payload.getBookingId()).ifPresent(booking -> {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setFailureReason(payload.getReason());
            bookingRepository.save(booking);

            BookingCancelledPayload outboxPayload = BookingCancelledPayload.builder()
                    .bookingId(payload.getBookingId())
                    .reason(payload.getReason())
                    .status("CANCELLED")
                    .build();

            outboxService.createEvent(OutboxEventData.builder()
                    .aggregateType(AggregateTypes.BOOKING)
                    .aggregateId(payload.getBookingId())
                    .eventType(Events.BOOKING_CANCELLED)
                    .payload(outboxPayload)
                    .build());
        });
    }

    @Override
    @Transactional
    public void handlePaymentProcessed(PaymentProcessedPayload payload) {
        log.info("Handling PaymentProcessed for bookingId: {}", payload.getBookingId());
        bookingRepository.findById(payload.getBookingId()).ifPresent(booking -> {
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);

            BookingConfirmedPayload outboxPayload = BookingConfirmedPayload.builder()
                    .bookingId(booking.getId())
                    .userId(booking.getUserId())
                    .movieId(booking.getMovieId())
                    .showtimeId(booking.getShowtimeId())
                    .seatIds(booking.getSeatIds())
                    .status("CONFIRMED")
                    .build();

            outboxService.createEvent(OutboxEventData.builder()
                    .aggregateType(AggregateTypes.BOOKING)
                    .aggregateId(booking.getId())
                    .eventType(Events.BOOKING_CONFIRMED)
                    .payload(outboxPayload)
                    .build());
        });
    }

    @Override
    @Transactional
    public void handlePaymentFailed(PaymentFailedPayload payload) {
        log.info("Handling PaymentFailed for bookingId: {}, reason: {}", payload.getBookingId(), payload.getReason());
        bookingRepository.findById(payload.getBookingId()).ifPresent(booking -> {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setFailureReason(payload.getReason());
            bookingRepository.save(booking);

            BookingCancelledPayload outboxPayload = BookingCancelledPayload.builder()
                    .bookingId(payload.getBookingId())
                    .reason(payload.getReason())
                    .status("CANCELLED")
                    .build();

            outboxService.createEvent(OutboxEventData.builder()
                    .aggregateType(AggregateTypes.BOOKING)
                    .aggregateId(payload.getBookingId())
                    .eventType(Events.BOOKING_CANCELLED)
                    .payload(outboxPayload)
                    .build());
        });
    }

    @Override
    public void handleSeatsCompensated(SeatsCompensatedPayload payload) {
        log.info("SeatsCompensated received for bookingId: {}", payload.getBookingId());
    }
}
