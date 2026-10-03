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
import org.springframework.dao.DataIntegrityViolationException;
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
        notification.setSourceApplication("repository-tests");
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
    void shouldFindByIdempotencyKeyInScope() {
        String key = "integration-idempotency-" + System.currentTimeMillis();

        Notification notification = new Notification();
        notification.setUser(currentUser);
        notification.setIdempotencyKey(key);
        notification.setSourceApplication("repository-tests");
        notification.setChannel(Channel.SMS);
        notification.setRecipient("+919198974465");
        notification.setStatus(NotificationStatus.PENDING);

        Notification saved = notificationRepository.save(notification);

        Notification found = notificationRepository
                .findByIdempotencyKeyAndUserAndSourceApplication(
                        key, currentUser, "repository-tests")
                .orElseThrow();

        assertEquals(saved.getId(), found.getId());
        assertTrue(notificationRepository
                .existsByIdempotencyKeyAndUserAndSourceApplication(
                        key, currentUser, "repository-tests"));

        notificationRepository.delete(found);
    }
    @Test
    void shouldRejectStaleUpdateWithOptimisticLocking() {

        Notification notification = new Notification();
        notification.setUser(currentUser);
        notification.setIdempotencyKey("lock-test-" + System.nanoTime());
        notification.setSourceApplication("repository-tests");
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

    @Test
    void shouldAllowSameUserAndKeyAcrossApplications() {
        String key = "scoped-user-app-" + System.nanoTime();

        Notification first = notification(key, currentUser, "app-one");
        Notification second = notification(key, currentUser, "app-two");

        notificationRepository.save(first);
        notificationRepository.save(second);

        assertTrue(notificationRepository
                .existsByIdempotencyKeyAndUserAndSourceApplication(
                        key, currentUser, "app-one"));
        assertTrue(notificationRepository
                .existsByIdempotencyKeyAndUserAndSourceApplication(
                        key, currentUser, "app-two"));

        notificationRepository.deleteAll(java.util.List.of(first, second));
    }

    @Test
    void shouldRejectSameUserApplicationAndKey() {
        String key = "scoped-duplicate-" + System.nanoTime();
        Notification first = notification(key, currentUser, "same-app");
        Notification duplicate = notification(key, currentUser, "same-app");

        notificationRepository.saveAndFlush(first);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> notificationRepository.saveAndFlush(duplicate)
        );

        notificationRepository.deleteById(first.getId());
    }

    @Test
    void shouldAllowSameApplicationAndKeyAcrossUsers() {
        String key = "scoped-users-" + System.nanoTime();
        AppUser secondUser = userRepository.save(
                new AppUser("notifyhub-" + System.nanoTime(), "encoded-pass", "USER"));

        Notification first = notification(key, currentUser, "shared-app");
        Notification second = notification(key, secondUser, "shared-app");

        notificationRepository.save(first);
        notificationRepository.save(second);

        assertTrue(notificationRepository
                .existsByIdempotencyKeyAndUserAndSourceApplication(
                        key, currentUser, "shared-app"));
        assertTrue(notificationRepository
                .existsByIdempotencyKeyAndUserAndSourceApplication(
                        key, secondUser, "shared-app"));

        notificationRepository.deleteAll(java.util.List.of(first, second));
        userRepository.delete(secondUser);
    }

    private Notification notification(String key, AppUser user, String sourceApplication) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setIdempotencyKey(key);
        notification.setSourceApplication(sourceApplication);
        notification.setChannel(Channel.EMAIL);
        notification.setRecipient("scope-test@example.com");
        notification.setStatus(NotificationStatus.PENDING);
        return notification;
    }
}