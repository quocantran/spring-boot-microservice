package com.moviebooking.common.idempotency;

import com.moviebooking.common.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, String> {
    Optional<ProcessedEventEntity> findByEventId(String eventId);
    void deleteByEventId(String eventId);
}
