package com.moviebooking.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "topups", indexes = {
        @Index(name = "idx_topup_user", columnList = "user_id"),
        @Index(name = "idx_topup_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopupEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "order_code", nullable = false, unique = true)
    private Long orderCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TopupStatus status = TopupStatus.PENDING;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "payment_link_id", length = 255)
    private String paymentLinkId;

    @Column(name = "transaction_reference", length = 255)
    private String transactionReference;

    @Column(name = "counter_account_bank_name", length = 255)
    private String counterAccountBankName;

    @Column(name = "counter_account_name", length = 255)
    private String counterAccountName;

    @Column(name = "counter_account_number", length = 255)
    private String counterAccountNumber;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
