package com.notifyhub.messaging.outbox;

import com.notifyhub.enums.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxEvent> findByStatusAndProcessingStartedAtBefore(
            OutboxStatus status, LocalDateTime time);

    @Modifying
    @Transactional
    @Query("""
        UPDATE OutboxEvent e
        SET e.status = com.notifyhub.enums.OutboxStatus.PROCESSING,
            e.processingStartedAt = :time,
            e.attempts = e.attempts + 1
        WHERE e.id = :id
        AND e.status = com.notifyhub.enums.OutboxStatus.PENDING
    """)
    int claimEvent(@Param("id") Long id, @Param("time") LocalDateTime time);
}