package com.notifyhub.repository;

import com.notifyhub.TestcontainersSupport;
import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.notification.repository.NotificationRepository;
import com.notifyhub.security.entity.AppUser;
import com.notifyhub.security.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class NotificationRepositoryIntegrationTest extends TestcontainersSupport {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15.8");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registerPostgresProperties(registry, POSTGRES);
    }

    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EntityManager entityManager;

    private AppUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = userRepository.findByUsername("notifyhub")
                .orElseGet(() -> userRepository.save(new AppUser("notifyhub", "encoded-pass", "USER")));
    }

    @Test
    void shouldSaveAndFindNotification() {
        Notification notification = new Notification();
        notification.setUser(currentUser);
        notification.setIdempotencyKey("integration-test-" + System.currentTimeMillis());
        notification.setChannel(Channel.EMAIL);
        notification.setRecipient("test@gmail.com");
        notification.setStatus(NotificationStatus.PENDING);
        notification.setRenderedContent("Integration test notification");

        Notification saved = notificationRepository.save(notification);

        assertNotNull(saved.getId());

        Notification found = notificationRepository.findById(saved.getId())
                .orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertEquals(Channel.EMAIL, found.getChannel());
        assertEquals("test@gmail.com", found.getRecipient());
        assertEquals(NotificationStatus.PENDING, found.getStatus());

        notificationRepository.delete(found);
    }

    @Test
    void shouldFindByIdempotencyKey() {
        String key = "integration-idempotency-" + System.currentTimeMillis();

        Notification notification = new Notification();
        notification.setUser(currentUser);
        notification.setIdempotencyKey(key);
        notification.setChannel(Channel.SMS);
        notification.setRecipient("+919198974465");
        notification.setStatus(NotificationStatus.PENDING);

        Notification saved = notificationRepository.save(notification);

        Notification found = notificationRepository.findByIdempotencyKey(key)
                .orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertTrue(notificationRepository.existsByIdempotencyKey(key));

        notificationRepository.delete(found);
    }
    @Test
    void shouldRejectStaleUpdateWithOptimisticLocking() {

        Notification notification = new Notification();
        notification.setUser(currentUser);
        notification.setIdempotencyKey("lock-test-" + System.nanoTime());
        notification.setChannel(Channel.EMAIL);
        notification.setRecipient("lock@gmail.com");
        notification.setStatus(NotificationStatus.PENDING);
        notification.setRenderedContent("Original");

        Notification saved = notificationRepository.saveAndFlush(notification);

        Notification first = notificationRepository.findById(saved.getId()).orElseThrow();
        Notification second = notificationRepository.findById(saved.getId()).orElseThrow();

        first.setRenderedContent("First update");
        notificationRepository.saveAndFlush(first);

        second.setRenderedContent("Stale update");

        assertThrows(
                ObjectOptimisticLockingFailureException.class,
                () -> notificationRepository.saveAndFlush(second)
        );

        notificationRepository.deleteById(saved.getId());
    }
}