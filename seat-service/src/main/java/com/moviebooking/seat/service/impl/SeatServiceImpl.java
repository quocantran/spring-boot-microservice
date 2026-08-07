package com.moviebooking.seat.service.impl;

import com.moviebooking.common.event.EventTypes.AggregateTypes;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.event.EventPayloads.*;
import com.moviebooking.common.outbox.OutboxService;
import com.moviebooking.common.outbox.OutboxService.OutboxEventData;
import com.moviebooking.common.redis.RedisLockService;
import com.moviebooking.common.redis.RedisLockService.LockResult;
import com.moviebooking.seat.entity.SeatEntity;
import com.moviebooking.seat.entity.SeatStatus;
import com.moviebooking.seat.repository.SeatRepository;
import com.moviebooking.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {

    private final SeatRepository seatRepository;
    private final OutboxService outboxService;
    private final RedisLockService redisLockService;

    private static final long REDIS_LOCK_TTL_MS = 5000;
    private static final long SEAT_HOLD_MINUTES = 5;

    @Override
    @Transactional(readOnly = true)
    public List<SeatEntity> findByShowtimeId(String showtimeId) {
        return seatRepository.findByShowtimeIdOrderBySeatRowAscSeatNumberAsc(showtimeId);
    }

    @Override
    public void reserveSeatsWithLock(BookingCreatedPayload payload) {
        String bookingId = payload.getBookingId();
        String showtimeId = payload.getShowtimeId();
        List<String> seatIds = payload.getSeatIds();

        List<String> lockKeys = seatIds.stream()
                .map(seatId -> "lock:seat:" + showtimeId + ":" + seatId)
                .collect(Collectors.toList());

        LockResult lockResult = redisLockService.acquireMultipleLocks(lockKeys, REDIS_LOCK_TTL_MS);

        if (!lockResult.isSuccess()) {
            emitReservationFailed(bookingId, showtimeId, seatIds, "Ghế đang được người khác đặt, vui lòng thử lại");
            return;
        }

        try {
            doReserveSeatsTransaction(payload);
        } catch (Exception e) {
            log.error("Failed to reserve seats for bookingId: {}", bookingId, e);
            emitReservationFailed(bookingId, showtimeId, seatIds, e.getMessage());
        } finally {
            redisLockService.releaseMultipleLocks(lockResult.getTokens());
        }
    }

    @Transactional
    public void doReserveSeatsTransaction(BookingCreatedPayload payload) {
        String bookingId = payload.getBookingId();
        String showtimeId = payload.getShowtimeId();
        List<String> seatIds = payload.getSeatIds();
        Instant expireAt = Instant.now().plus(Duration.ofMinutes(SEAT_HOLD_MINUTES));

        int affected = seatRepository.reserveAvailableSeats(
                seatIds, showtimeId, bookingId, expireAt, SeatStatus.HELD, SeatStatus.AVAILABLE
        );

        if (affected != seatIds.size()) {
            if (affected > 0) {
                seatRepository.rollbackHeldSeats(bookingId, SeatStatus.HELD, SeatStatus.AVAILABLE);
            }
            throw new RuntimeException("Một số ghế không khả dụng (yêu cầu " + seatIds.size() + ", khả dụng " + affected + ")");
        }

        SeatsReservedPayload outboxPayload = SeatsReservedPayload.builder()
                .bookingId(bookingId)
                .userId(payload.getUserId())
                .showtimeId(showtimeId)
                .seatIds(seatIds)
                .totalAmount(payload.getTotalAmount())
                .build();

        outboxService.createEvent(OutboxEventData.builder()
                .aggregateType(AggregateTypes.SEAT)
                .aggregateId(bookingId)
                .eventType(Events.SEATS_RESERVED)
                .payload(outboxPayload)
                .build());

        log.info("Successfully reserved {} seats for bookingId: {}", affected, bookingId);
    }

    @Override
    @Transactional
    public void confirmSeats(String bookingId) {
        int affected = seatRepository.confirmHeldSeats(
                bookingId, Instant.now(), SeatStatus.HELD, SeatStatus.BOOKED
        );
        log.info("Confirmed {} seats for bookingId: {}", affected, bookingId);

        if (affected > 0) {
            outboxService.createEvent(OutboxEventData.builder()
                    .aggregateType(AggregateTypes.SEAT)
                    .aggregateId(bookingId)
                    .eventType(Events.SEATS_RESERVED)
                    .payload(Map.of("bookingId", bookingId, "confirmed", true))
                    .build());
        }
    }

    @Override
    @Transactional
    public void compensateSeats(String bookingId, String showtimeId, List<String> seatIds, String reason) {
        int affected = seatRepository.compensateSeats(seatIds, bookingId, SeatStatus.AVAILABLE);
        log.info("Compensated {} seats for bookingId: {}, reason: {}", affected, bookingId, reason);

        SeatsCompensatedPayload outboxPayload = SeatsCompensatedPayload.builder()
                .bookingId(bookingId)
                .showtimeId(showtimeId)
                .seatIds(seatIds)
                .reason(reason)
                .build();

        outboxService.createEvent(OutboxEventData.builder()
                .aggregateType(AggregateTypes.SEAT)
                .aggregateId(bookingId)
                .eventType(Events.SEATS_COMPENSATED)
                .payload(outboxPayload)
                .build());
    }

    @Override
    @Transactional
    public List<SeatEntity> generateSeatsForShowtime(String showtimeId, Integer rows, Integer cols) {
        int r = (rows != null && rows > 0) ? rows : 5;
        int c = (cols != null && cols > 0) ? cols : 8;

        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        List<SeatEntity> seats = new ArrayList<>();

        for (int i = 0; i < r; i++) {
            String rowLabel = String.valueOf(alphabet.charAt(i % alphabet.length()));
            for (int col = 1; col <= c; col++) {
                String seatNum = rowLabel + col;
                SeatEntity seat = SeatEntity.builder()
                        .id("seat-" + showtimeId + "-" + seatNum)
                        .showtimeId(showtimeId)
                        .seatNumber(seatNum)
                        .seatRow(rowLabel)
                        .status(SeatStatus.AVAILABLE)
                        .bookingId(null)
                        .expireAt(null)
                        .build();
                seats.add(seat);
            }
        }

        return seatRepository.saveAll(seats);
    }

    @Override
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void cleanupExpiredHolds() {
        try {
            int cleaned = seatRepository.cleanupExpiredHolds(Instant.now(), SeatStatus.HELD, SeatStatus.AVAILABLE);
            if (cleaned > 0) {
                log.info("Cleaned up {} expired seat holds", cleaned);
            }
        } catch (Exception e) {
            log.error("Failed to cleanup expired seat holds", e);
        }
    }

    private void emitReservationFailed(String bookingId, String showtimeId, List<String> seatIds, String reason) {
        SeatReservationFailedPayload payload = SeatReservationFailedPayload.builder()
                .bookingId(bookingId)
                .showtimeId(showtimeId)
                .seatIds(seatIds)
                .reason(reason)
                .build();

        outboxService.createEvent(OutboxEventData.builder()
                .aggregateType(AggregateTypes.SEAT)
                .aggregateId(bookingId)
                .eventType(Events.SEAT_RESERVATION_FAILED)
                .payload(payload)
                .build());
    }
}
