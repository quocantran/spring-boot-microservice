package com.moviebooking.common.idempotency;

import com.moviebooking.common.entity.ProcessedEventEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;
    private final TransactionTemplate transactionTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaimEvent(String eventId, String eventType) {
        try {
            ProcessedEventEntity entity = new ProcessedEventEntity(eventId, eventType);
            processedEventRepository.saveAndFlush(entity);
            return true;
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate event detected, eventId: {}, eventType: {}", eventId, eventType);
            return false;
        } catch (Exception e) {
            log.warn("Error claiming eventId: {}", eventId, e);
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseClaim(String eventId) {
        try {
            processedEventRepository.deleteByEventId(eventId);
        } catch (Exception e) {
            log.warn("Failed to release claim for eventId: {}", eventId, e);
        }
    }

    public boolean processWithIdempotency(String eventId, String eventType, Runnable handler) {
        Boolean claimed = transactionTemplate.execute(status -> tryClaimEvent(eventId, eventType));
        if (claimed == null || !claimed) {
            return false;
        }

        try {
            handler.run();
            return true;
        } catch (Exception e) {
            transactionTemplate.executeWithoutResult(status -> releaseClaim(eventId));
            throw e;
        }
    }
}
