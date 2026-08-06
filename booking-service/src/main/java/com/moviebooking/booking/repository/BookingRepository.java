package com.moviebooking.booking.repository;

import com.moviebooking.booking.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<BookingEntity, String> {

    List<BookingEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
