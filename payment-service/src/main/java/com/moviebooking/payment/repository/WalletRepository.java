package com.moviebooking.payment.repository;

import com.moviebooking.payment.entity.WalletEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface WalletRepository extends JpaRepository<WalletEntity, String> {

    @Modifying
    @Query("UPDATE WalletEntity w SET w.balance = w.balance + :amount WHERE w.userId = :userId")
    int creditBalance(@Param("userId") String userId, @Param("amount") BigDecimal amount);

    @Modifying
    @Query("UPDATE WalletEntity w SET w.balance = w.balance - :amount WHERE w.userId = :userId AND w.balance >= :amount")
    int debitBalance(@Param("userId") String userId, @Param("amount") BigDecimal amount);
}
