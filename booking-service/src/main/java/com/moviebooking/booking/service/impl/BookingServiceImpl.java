package com.moviebooking.booking.service.impl;

import com.moviebooking.booking.dto.CreateBookingRequest;
import com.moviebooking.booking.entity.BookingEntity;
import com.moviebooking.booking.entity.BookingStatus;
import com.moviebooking.booking.realtime.BookingRealtimePublisher;
import com.moviebooking.booking.repository.BookingRepository;
import com.moviebooking.booking.service.BookingService;
import com.moviebooking.common.constants.BookingConstants;
import com.moviebooking.common.event.EventPayloads.*;
import com.moviebooking.common.event.OutboxEventFactory;
import com.moviebooking.common.exception.CustomExceptions.BadRequestException;
import com.moviebooking.common.exception.CustomExceptions.NotFoundException;
import com.moviebooking.common.outbox.OutboxService;
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
    private final BookingRealtimePublisher bookingRealtimePublisher;

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
        bookingRealtimePublisher.publishBookingUpdate(booking);

        BookingCreatedPayload payload = BookingCreatedPayload.builder()
                .bookingId(bookingId)
                .userId(userId)
                .movieId(request.getMovieId())
                .showtimeId(request.getShowtimeId())
                .seatIds(request.getSeatIds())
                .totalAmount(request.getTotalAmount() != null ? request.getTotalAmount().doubleValue() : null)
                .build();

        outboxService.createEvent(OutboxEventFactory.bookingCreated(bookingId, payload));

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
            bookingRealtimePublisher.publishBookingUpdate(booking);
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
            bookingRealtimePublisher.publishBookingUpdate(booking);

            BookingCancelledPayload outboxPayload = BookingCancelledPayload.builder()
                    .bookingId(payload.getBookingId())
                    .reason(payload.getReason())
                    .status(BookingConstants.STATUS_CANCELLED)
                    .build();

            outboxService.createEvent(OutboxEventFactory.bookingCancelled(payload.getBookingId(), outboxPayload));
        });
    }

    @Override
    @Transactional
    public void handlePaymentProcessed(PaymentProcessedPayload payload) {
        log.info("Handling PaymentProcessed for bookingId: {}", payload.getBookingId());
        bookingRepository.findById(payload.getBookingId()).ifPresent(booking -> {
            booking.setStatus(BookingStatus.CONFIRMED);
            bookingRepository.save(booking);
            bookingRealtimePublisher.publishBookingUpdate(booking);

            BookingConfirmedPayload outboxPayload = BookingConfirmedPayload.builder()
                    .bookingId(booking.getId())
                    .userId(booking.getUserId())
                    .movieId(booking.getMovieId())
                    .showtimeId(booking.getShowtimeId())
                    .seatIds(booking.getSeatIds())
                    .status(BookingConstants.STATUS_CONFIRMED)
                    .build();

            outboxService.createEvent(OutboxEventFactory.bookingConfirmed(booking.getId(), outboxPayload));
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
            bookingRealtimePublisher.publishBookingUpdate(booking);

            BookingCancelledPayload outboxPayload = BookingCancelledPayload.builder()
                    .bookingId(payload.getBookingId())
                    .reason(payload.getReason())
                    .status(BookingConstants.STATUS_CANCELLED)
                    .build();

            outboxService.createEvent(OutboxEventFactory.bookingCancelled(payload.getBookingId(), outboxPayload));
        });
    }

    @Override
    public void handleSeatsCompensated(SeatsCompensatedPayload payload) {
        log.info("SeatsCompensated received for bookingId: {}", payload.getBookingId());
    }
}
