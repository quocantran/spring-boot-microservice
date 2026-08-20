package com.moviebooking.payment.service.impl;

import com.moviebooking.common.event.EventPayloads.PaymentFailedPayload;
import com.moviebooking.common.event.EventPayloads.PaymentProcessedPayload;
import com.moviebooking.common.event.EventPayloads.SeatsReservedPayload;
import com.moviebooking.common.event.EventTypes.AggregateTypes;
import com.moviebooking.common.event.EventTypes.Events;
import com.moviebooking.common.outbox.OutboxService;
import com.moviebooking.payment.entity.PaymentEntity;
import com.moviebooking.payment.entity.PaymentStatus;
import com.moviebooking.payment.entity.WalletEntity;
import com.moviebooking.payment.realtime.WalletRealtimePublisher;
import com.moviebooking.payment.repository.PaymentRepository;
import com.moviebooking.payment.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private WalletRealtimePublisher walletRealtimePublisher;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private SeatsReservedPayload createSamplePayload(String userId, double amount) {
        return SeatsReservedPayload.builder()
                .bookingId("b-12345")
                .userId(userId)
                .showtimeId("st-999")
                .seatIds(List.of("A1", "A2"))
                .totalAmount(amount)
                .build();
    }

    @Nested
    @DisplayName("1.1 Core Payment Processing")
    class CorePaymentProcessingTests {

        @Test
        @DisplayName("Case 1: Should fail payment and emit PAYMENT_FAILED event when user has no wallet")
        void should_FailPayment_when_UserHasNoWallet() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-no-wallet", 150000.0);
            when(walletRepository.findById("user-no-wallet")).thenReturn(Optional.empty());

            // When
            paymentService.processPayment(payload);

            // Then
            ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            PaymentEntity savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(savedPayment.getFailureReason()).contains("Không tìm thấy ví tài khoản người dùng: user-no-wallet");
            assertThat(savedPayment.getBookingId()).isEqualTo("b-12345");
            assertThat(savedPayment.getUserId()).isEqualTo("user-no-wallet");

            verify(outboxService).createEvent(argThat(event ->
                    AggregateTypes.PAYMENT.equals(event.getAggregateType()) &&
                    Events.PAYMENT_FAILED.equals(event.getEventType()) &&
                    "b-12345".equals(event.getAggregateId())
            ));

            verifyNoInteractions(walletRealtimePublisher);
        }

        @Test
        @DisplayName("Case 2: Should fail payment and emit PAYMENT_FAILED event when balance is insufficient")
        void should_FailPayment_when_BalanceIsInsufficient() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-low-balance", 150000.0);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-low-balance")
                    .balance(BigDecimal.valueOf(100000.0))
                    .build();
            when(walletRepository.findById("user-low-balance")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            PaymentEntity savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(savedPayment.getFailureReason()).contains("Tài khoản không đủ số dư. Số dư: 100000.0 VNĐ, Yêu cầu: 150000.0 VNĐ");

            verify(outboxService).createEvent(argThat(event ->
                    Events.PAYMENT_FAILED.equals(event.getEventType())
            ));
        }

        @Test
        @DisplayName("Case 3: Should fail payment when balance is exactly zero")
        void should_FailPayment_when_BalanceIsZero() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-zero-balance", 150000.0);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-zero-balance")
                    .balance(BigDecimal.ZERO)
                    .build();
            when(walletRepository.findById("user-zero-balance")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(paymentCaptor.getValue().getFailureReason()).contains("Tài khoản không đủ số dư");
        }

        @Test
        @DisplayName("Case 4: Should fail payment when balance is exactly one unit less than amount")
        void should_FailPayment_when_BalanceIsExactlyOneLessThanAmount() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-almost-enough", 150000.0);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-almost-enough")
                    .balance(BigDecimal.valueOf(149999.0))
                    .build();
            when(walletRepository.findById("user-almost-enough")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @Test
        @DisplayName("Case 5: Should debit wallet when balance is sufficient")
        void should_DebitWallet_when_BalanceSufficient() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-rich", 150000.0);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-rich")
                    .balance(BigDecimal.valueOf(300000.0))
                    .build();
            when(walletRepository.findById("user-rich")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(150000.0));
            verify(walletRepository).save(wallet);
        }

        @Test
        @DisplayName("Case 6: Should create PROCESSED payment record when payment succeeds")
        void should_CreateProcessedPaymentRecord_when_PaymentSucceeds() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-ok", 200000.0);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-ok")
                    .balance(BigDecimal.valueOf(500000.0))
                    .build();
            when(walletRepository.findById("user-ok")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            ArgumentCaptor<PaymentEntity> paymentCaptor = ArgumentCaptor.forClass(PaymentEntity.class);
            verify(paymentRepository).save(paymentCaptor.capture());
            PaymentEntity savedPayment = paymentCaptor.getValue();
            assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSED);
            assertThat(savedPayment.getBookingId()).isEqualTo("b-12345");
            assertThat(savedPayment.getUserId()).isEqualTo("user-ok");
            assertThat(savedPayment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(200000.0));
        }

        @Test
        @DisplayName("Case 7: Should emit PAYMENT_PROCESSED outbox event when payment succeeds")
        void should_EmitPaymentProcessedOutboxEvent_when_PaymentSucceeds() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-ok", 120000.0);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-ok")
                    .balance(BigDecimal.valueOf(300000.0))
                    .build();
            when(walletRepository.findById("user-ok")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            verify(outboxService).createEvent(argThat(event -> {
                boolean matchType = AggregateTypes.PAYMENT.equals(event.getAggregateType()) &&
                        Events.PAYMENT_PROCESSED.equals(event.getEventType()) &&
                        "b-12345".equals(event.getAggregateId());
                if (!matchType) return false;
                PaymentProcessedPayload p = (PaymentProcessedPayload) event.getPayload();
                return "b-12345".equals(p.getBookingId()) && Double.valueOf(120000.0).equals(p.getAmount());
            }));
        }

        @Test
        @DisplayName("Case 8: Should publish wallet update via SSE when payment succeeds")
        void should_PublishWalletUpdateViaSSE_when_PaymentSucceeds() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-sse", 100000.0);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-sse")
                    .balance(BigDecimal.valueOf(250000.0))
                    .build();
            when(walletRepository.findById("user-sse")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            verify(walletRealtimePublisher).publishWalletUpdate("user-sse", 150000.0);
        }

        @Test
        @DisplayName("Case 9: Should debit exact amount when balance equals amount exactly")
        void should_DebitExactAmount_when_BalanceEqualsAmount() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-exact", 150000.0);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-exact")
                    .balance(BigDecimal.valueOf(150000.0))
                    .build();
            when(walletRepository.findById("user-exact")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(walletRepository).save(wallet);
            verify(walletRealtimePublisher).publishWalletUpdate("user-exact", 0.0);
        }

        @Test
        @DisplayName("Case 10: Should not modify wallet when payment fails due to insufficient balance")
        void should_NotModifyWallet_when_PaymentFails() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-not-modified", 200000.0);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-not-modified")
                    .balance(BigDecimal.valueOf(50000.0))
                    .build();
            when(walletRepository.findById("user-not-modified")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            assertThat(wallet.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(50000.0));
            verify(walletRepository, never()).save(wallet);
        }

        @Test
        @DisplayName("Case 11: Should include correct seatIds and showtimeId in payment failed event")
        void should_IncludeCorrectSeatIdsAndShowtimeId_inPaymentFailedEvent() {
            // Given
            SeatsReservedPayload payload = SeatsReservedPayload.builder()
                    .bookingId("b-custom-fail")
                    .userId("user-custom-fail")
                    .showtimeId("showtime-456")
                    .seatIds(List.of("B1", "B2", "B3"))
                    .totalAmount(300000.0)
                    .build();
            when(walletRepository.findById("user-custom-fail")).thenReturn(Optional.empty());

            // When
            paymentService.processPayment(payload);

            // Then
            verify(outboxService).createEvent(argThat(event -> {
                if (!Events.PAYMENT_FAILED.equals(event.getEventType())) return false;
                PaymentFailedPayload p = (PaymentFailedPayload) event.getPayload();
                return "b-custom-fail".equals(p.getBookingId()) &&
                        "showtime-456".equals(p.getShowtimeId()) &&
                        p.getSeatIds().containsAll(List.of("B1", "B2", "B3")) &&
                        p.getReason().contains("Không tìm thấy ví");
            }));
        }

        @Test
        @DisplayName("Case 12: Should not publish SSE when payment fails")
        void should_NotPublishSSE_when_PaymentFails() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-no-sse", 100000.0);
            when(walletRepository.findById("user-no-sse")).thenReturn(Optional.empty());

            // When
            paymentService.processPayment(payload);

            // Then
            verifyNoInteractions(walletRealtimePublisher);
        }
    }

    @Nested
    @DisplayName("1.2 Business Invariants — Payment")
    class PaymentBusinessInvariantsTests {

        @Test
        @DisplayName("Invariant 13: Payment FAILED -> wallet balance must not change")
        void invariant_WalletBalanceUnchanged_when_PaymentFailed() {
            // Given
            BigDecimal initialBalance = BigDecimal.valueOf(50000.0);
            SeatsReservedPayload payload = createSamplePayload("user-inv-fail", 100000.0);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-inv-fail")
                    .balance(initialBalance)
                    .build();
            when(walletRepository.findById("user-inv-fail")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            assertThat(wallet.getBalance()).isEqualByComparingTo(initialBalance);
            verify(walletRepository, never()).save(wallet);
        }

        @Test
        @DisplayName("Invariant 14: Payment PROCESSED -> wallet balance must decrease by exact amount")
        void invariant_WalletBalanceDecreasedByExactAmount_when_PaymentProcessed() {
            // Given
            BigDecimal initialBalance = BigDecimal.valueOf(500000.0);
            Double amountToDebit = 175000.0;
            SeatsReservedPayload payload = createSamplePayload("user-inv-ok", amountToDebit);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-inv-ok")
                    .balance(initialBalance)
                    .build();
            when(walletRepository.findById("user-inv-ok")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            ArgumentCaptor<WalletEntity> walletCaptor = ArgumentCaptor.forClass(WalletEntity.class);
            verify(walletRepository).save(walletCaptor.capture());
            BigDecimal savedBalance = walletCaptor.getValue().getBalance();
            BigDecimal expectedBalance = initialBalance.subtract(BigDecimal.valueOf(amountToDebit));
            assertThat(savedBalance).isEqualByComparingTo(expectedBalance);
        }

        @Test
        @DisplayName("Invariant 15: Payment PROCESSED -> PAYMENT_PROCESSED event must exist in Outbox")
        void invariant_PaymentProcessedEventExists_when_PaymentSucceeds() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-inv-event", 80000.0);
            WalletEntity wallet = WalletEntity.builder()
                    .userId("user-inv-event")
                    .balance(BigDecimal.valueOf(200000.0))
                    .build();
            when(walletRepository.findById("user-inv-event")).thenReturn(Optional.of(wallet));

            // When
            paymentService.processPayment(payload);

            // Then
            verify(outboxService, times(1)).createEvent(argThat(event ->
                    AggregateTypes.PAYMENT.equals(event.getAggregateType()) &&
                    Events.PAYMENT_PROCESSED.equals(event.getEventType()) &&
                    payload.getBookingId().equals(event.getAggregateId())
            ));
        }

        @Test
        @DisplayName("Invariant 16: Payment FAILED -> PAYMENT_FAILED event must exist in Outbox")
        void invariant_PaymentFailedEventExists_when_PaymentFails() {
            // Given
            SeatsReservedPayload payload = createSamplePayload("user-inv-event-fail", 80000.0);
            when(walletRepository.findById("user-inv-event-fail")).thenReturn(Optional.empty());

            // When
            paymentService.processPayment(payload);

            // Then
            verify(outboxService, times(1)).createEvent(argThat(event ->
                    AggregateTypes.PAYMENT.equals(event.getAggregateType()) &&
                    Events.PAYMENT_FAILED.equals(event.getEventType()) &&
                    payload.getBookingId().equals(event.getAggregateId())
            ));
        }
    }
}
