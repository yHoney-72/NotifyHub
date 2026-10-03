package com.notifyhub.messaging.outbox;

import com.notifyhub.TestcontainersSupport;
import com.notifyhub.enums.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class OutboxEventRepositoryIntegrationTest extends TestcontainersSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15.8");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry, POSTGRES);
    }

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void shouldClaimPendingEventAndUpdateProcessingState() {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("Notification");
        event.setAggregateId(1L);
        event.setEventType("NotificationCreated");
        event.setPayload("{}");
        event.setStatus(OutboxStatus.PENDING);
        event.setAttempts(0);

        OutboxEvent saved = outboxEventRepository.saveAndFlush(event);
        LocalDateTime processingTime = LocalDateTime.now().withNano(0);

        int claimed = outboxEventRepository.claimEvent(saved.getId(), processingTime);

        assertEquals(1, claimed);

        OutboxEvent claimedEvent = outboxEventRepository.findById(saved.getId()).orElseThrow();
        assertEquals(OutboxStatus.PROCESSING, claimedEvent.getStatus());
        assertEquals(1, claimedEvent.getAttempts());
        assertNotNull(claimedEvent.getProcessingStartedAt());
        assertEquals(processingTime, claimedEvent.getProcessingStartedAt());

        outboxEventRepository.deleteById(saved.getId());
    }
}
