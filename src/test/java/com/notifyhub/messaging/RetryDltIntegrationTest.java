package com.notifyhub.messaging;

import com.notifyhub.TestcontainersSupport;
import com.notifyhub.engine.NotificationEngine;
import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.messaging.event.NotificationEvent;
import com.notifyhub.messaging.producer.NotificationEventProducer;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.notification.repository.NotificationRepository;
import com.notifyhub.security.entity.AppUser;
import com.notifyhub.security.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "spring.kafka.consumer.group-id=notifyhub-test-group-dlt-isolated",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RetryDltIntegrationTest extends TestcontainersSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15.8");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry, POSTGRES);
    }
    @Autowired
    private NotificationEventProducer eventProducer;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;
    @MockitoBean
    private NotificationEngine notificationEngine;

    private AppUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = userRepository.findByUsername("notifyhub")
                .orElseGet(() -> userRepository.save(new AppUser("notifyhub", "encoded-pass", "USER")));
    }

    @Test
    void shouldMoveFailedNotificationToDlt() throws Exception {
        doThrow(new IllegalStateException("TEST_FAILURE"))
                .when(notificationEngine)
                .process(any(NotificationEvent.class));

        Notification notification=new Notification();
        notification.setUser(currentUser);
        notification.setIdempotencyKey("dlt-test-"+System.nanoTime());
        notification.setSourceApplication("retry-tests");
        notification.setChannel(Channel.EMAIL);
        notification.setRecipient("dlt-test@gmail.com");
        notification.setRenderedContent("DLT integration test");
        notification.setStatus(NotificationStatus.PENDING);

        Notification saved=notificationRepository.save(notification);

        NotificationEvent event=new NotificationEvent(
                saved.getId(),
                Channel.EMAIL,
                saved.getRecipient(),
                null,
                Map.of(),
                "DLT Test",
                saved.getRenderedContent()
        );

        eventProducer.publish(event);

        verify(notificationEngine,timeout(30000).atLeastOnce())
                .process(any(NotificationEvent.class));

        long timeout=System.currentTimeMillis()+30000;
        Notification updated=null;

        while(System.currentTimeMillis()<timeout){
            updated=notificationRepository.findById(saved.getId()).orElse(null);
            if(updated!=null&&updated.getStatus()==NotificationStatus.FAILED) break;
            Thread.sleep(500);
        }

        assertNotNull(updated);
        assertEquals(NotificationStatus.FAILED,updated.getStatus());
        assertEquals("Notification delivery exhausted retries after DLT for notification " + saved.getId(), updated.getFailureReason());

        notificationRepository.deleteById(saved.getId());
    }
}