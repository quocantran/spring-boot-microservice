package com.moviebooking.payment.repository;

import com.moviebooking.payment.entity.TopupEntity;
import com.moviebooking.payment.entity.TopupStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TopupRepository extends JpaRepository<TopupEntity, String> {

    Optional<TopupEntity> findByOrderCode(Long orderCode);

    Optional<TopupEntity> findByOrderCodeAndUserId(Long orderCode, String userId);

    List<TopupEntity> findByUserIdOrderByCreatedAtDesc(String userId);

    @Modifying
    @Query("UPDATE TopupEntity t SET t.status = :newStatus WHERE t.orderCode = :orderCode AND t.status = :currentStatus")
    int updateStatusByOrderCode(
            @Param("orderCode") Long orderCode,
            @Param("currentStatus") TopupStatus currentStatus,
            @Param("newStatus") TopupStatus newStatus
    );

    @Modifying
    @Query("UPDATE TopupEntity t SET t.status = :newStatus, " +
            "t.transactionReference = :transactionReference, " +
            "t.counterAccountBankName = :counterAccountBankName, " +
            "t.counterAccountName = :counterAccountName, " +
            "t.counterAccountNumber = :counterAccountNumber, " +
            "t.paidAt = :paidAt " +
            "WHERE t.orderCode = :orderCode AND t.status = :currentStatus")
    int markAsPaid(
            @Param("orderCode") Long orderCode,
            @Param("currentStatus") TopupStatus currentStatus,
            @Param("newStatus") TopupStatus newStatus,
            @Param("transactionReference") String transactionReference,
            @Param("counterAccountBankName") String counterAccountBankName,
            @Param("counterAccountName") String counterAccountName,
            @Param("counterAccountNumber") String counterAccountNumber,
            @Param("paidAt") Instant paidAt
    );
}
