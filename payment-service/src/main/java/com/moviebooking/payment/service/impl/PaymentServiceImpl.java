package com.moviebooking.payment.service.impl;

import com.moviebooking.common.event.EventPayloads.*;
import com.moviebooking.common.event.EventTypes.AggregateTypes;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.outbox.OutboxService;
import com.moviebooking.common.outbox.OutboxService.OutboxEventData;
import com.moviebooking.payment.entity.PaymentEntity;
import com.moviebooking.payment.entity.PaymentStatus;
import com.moviebooking.payment.entity.WalletEntity;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.payment.repository.WalletRepository;
import com.moviebooking.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final WalletRepository walletRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;

    @Override
    @Transactional
    public void processPayment(SeatsReservedPayload payload) {
        String bookingId = payload.getBookingId();
        String userId = payload.getUserId();
        String showtimeId = payload.getShowtimeId();
        List<String> seatIds = payload.getSeatIds();
        Double totalAmount = payload.getTotalAmount();
        BigDecimal amount = BigDecimal.valueOf(totalAmount);

        Optional<WalletEntity> walletOpt = walletRepository.findById(userId);

        // Case 1: Wallet not found
        if (walletOpt.isEmpty()) {
            String reason = "Không tìm thấy ví tài khoản người dùng: " + userId;

            PaymentEntity payment = PaymentEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .bookingId(bookingId)
                    .userId(userId)
                    .amount(amount)
                    .status(PaymentStatus.FAILED)
                    .failureReason(reason)
                    .build();
            paymentRepository.save(payment);

            outboxService.createEvent(OutboxEventData.builder()
                    .aggregateType(AggregateTypes.PAYMENT)
                    .aggregateId(bookingId)
                    .eventType(Events.PAYMENT_FAILED)
                    .payload(PaymentFailedPayload.builder()
                            .bookingId(bookingId)
                            .showtimeId(showtimeId)
                            .seatIds(seatIds)
                            .reason(reason)
                            .build())
                    .build());
            return;
        }

        WalletEntity wallet = walletOpt.get();
        BigDecimal balance = wallet.getBalance();

        // Case 2: Insufficient balance
        if (balance.compareTo(amount) < 0) {
            String reason = "Tài khoản không đủ số dư. Số dư: " + balance + " VNĐ, Yêu cầu: " + totalAmount + " VNĐ";

            PaymentEntity payment = PaymentEntity.builder()
                    .id(UUID.randomUUID().toString())
                    .bookingId(bookingId)
                    .userId(userId)
                    .amount(amount)
                    .status(PaymentStatus.FAILED)
                    .failureReason(reason)
                    .build();
            paymentRepository.save(payment);

            outboxService.createEvent(OutboxEventData.builder()
                    .aggregateType(AggregateTypes.PAYMENT)
                    .aggregateId(bookingId)
                    .eventType(Events.PAYMENT_FAILED)
                    .payload(PaymentFailedPayload.builder()
                            .bookingId(bookingId)
                            .showtimeId(showtimeId)
                            .seatIds(seatIds)
                            .reason(reason)
                            .build())
                    .build());
            return;
        }

        // Case 3: Success - debit wallet and create PROCESSED payment
        BigDecimal newBalance = balance.subtract(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        String paymentId = UUID.randomUUID().toString();
        PaymentEntity payment = PaymentEntity.builder()
                .id(paymentId)
                .bookingId(bookingId)
                .userId(userId)
                .amount(amount)
                .status(PaymentStatus.PROCESSED)
                .build();
        paymentRepository.save(payment);

        outboxService.createEvent(OutboxEventData.builder()
                .aggregateType(AggregateTypes.PAYMENT)
                .aggregateId(bookingId)
                .eventType(Events.PAYMENT_PROCESSED)
                .payload(PaymentProcessedPayload.builder()
                        .bookingId(bookingId)
                        .paymentId(paymentId)
                        .amount(totalAmount)
                        .build())
                .build());

        log.info("Payment processed: bookingId={}, paymentId={}, amount={}", bookingId, paymentId, totalAmount);
    }
}
