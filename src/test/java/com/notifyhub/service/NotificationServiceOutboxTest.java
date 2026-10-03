package com.notifyhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notifyhub.enums.Channel;
import com.notifyhub.messaging.outbox.OutboxEvent;
import com.notifyhub.messaging.outbox.OutboxEventRepository;
import com.notifyhub.monitoring.NotifyHubMetrics;
import com.notifyhub.notification.dto.NotificationRequestDTO;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.notification.idempotency.IdempotencyService;
import com.notifyhub.notification.mapper.NotificationMapper;
import com.notifyhub.notification.repository.NotificationRepository;
import com.notifyhub.notification.sender.NotificationSender;
import com.notifyhub.notification.service.NotificationServiceImpl;
import com.notifyhub.security.entity.AppUser;
import com.notifyhub.security.repository.UserRepository;
import com.notifyhub.template.renderer.TemplateRenderer;
import com.notifyhub.template.repository.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceOutboxTest {
    @Mock private NotificationRepository notificationRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private TemplateRenderer templateRenderer;
    @Mock private IdempotencyService idempotencyService;
    @Mock private UserRepository userRepository;
    @Mock private NotificationSender notificationSender;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private NotifyHubMetrics metrics;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        AppUser currentUser = new AppUser("notifyhub", "encoded-pass", "USER");
        currentUser.setId(10L);

        when(userRepository.findByUsername("notifyhub"))
                .thenReturn(Optional.of(currentUser));

        when(notificationSender.getChannel())
                .thenReturn(Channel.EMAIL);

        notificationService = new NotificationServiceImpl(
                notificationRepository,
                templateRepository,
                notificationMapper,
                templateRenderer,
                idempotencyService,
                userRepository,
                List.of(notificationSender),
                outboxEventRepository,
                new ObjectMapper(),
                metrics
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "notifyhub",
                        "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );
    }

    @Test
    void shouldSaveOutboxEventWithNotification() {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("test@example.com");
        request.setRawSubject("Subject");
        request.setRawBody("Body");
        request.setIdempotencyKey("outbox-test-1");
        request.setSourceApplication("orders-service");

        Notification notification = new Notification();
        Notification saved = new Notification();
        saved.setId(1L);

        when(idempotencyService.tryAcquire(10L, "orders-service", "outbox-test-1"))
                .thenReturn(true);

        when(notificationRepository.existsByIdempotencyKeyAndUserAndSourceApplication(
                eq("outbox-test-1"), any(), eq("orders-service")))
                .thenReturn(false);

        when(notificationMapper.toEntity(request))
                .thenReturn(notification);

        when(notificationRepository.save(notification))
                .thenReturn(saved);

        when(notificationMapper.toResponse(saved))
                .thenReturn(new com.notifyhub.notification.dto.NotificationResponseDTO());

        notificationService.sendNotification(request);

        ArgumentCaptor<OutboxEvent> captor =
                ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent outboxEvent = captor.getValue();

        assertEquals("Notification", outboxEvent.getAggregateType());
        assertEquals(1L, outboxEvent.getAggregateId());
        assertEquals("NotificationCreated", outboxEvent.getEventType());
        assertEquals(
                com.notifyhub.enums.OutboxStatus.PENDING,
                outboxEvent.getStatus()
        );

        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }
}