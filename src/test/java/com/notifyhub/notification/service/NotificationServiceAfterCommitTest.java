package com.notifyhub.notification.service;

import com.notifyhub.enums.Channel;
import com.notifyhub.messaging.event.NotificationEvent;
import com.notifyhub.messaging.producer.NotificationEventProducer;
import com.notifyhub.notification.dto.NotificationRequestDTO;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.notification.idempotency.IdempotencyService;
import com.notifyhub.notification.mapper.NotificationMapper;
import com.notifyhub.notification.repository.NotificationRepository;
import com.notifyhub.notification.sender.NotificationSender;
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
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceAfterCommitTest {
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationEventProducer notificationEventProducer;
    @Mock private TemplateRepository templateRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private TemplateRenderer templateRenderer;
    @Mock private IdempotencyService idempotencyService;
    @Mock private UserRepository userRepository;
    @Mock private NotificationSender notificationSender;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        AppUser currentUser = new AppUser("notifyhub", "encoded-pass", "USER");
        currentUser.setId(10L);
        when(userRepository.findByUsername("notifyhub")).thenReturn(Optional.of(currentUser));
        when(notificationSender.getChannel()).thenReturn(Channel.EMAIL);
        notificationService = new NotificationServiceImpl(
                notificationRepository,
                notificationEventProducer,
                templateRepository,
                notificationMapper,
                templateRenderer,
                idempotencyService,
                userRepository,
                List.of(notificationSender)
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
    void shouldRegisterPublishOnlyAfterCommit() {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("test@example.com");
        request.setRawSubject("Subject");
        request.setRawBody("Body");
        request.setIdempotencyKey("after-commit-1");

        Notification notification = new Notification();
        Notification saved = new Notification();
        saved.setId(1L);

        when(idempotencyService.tryAcquire("after-commit-1")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUser(eq("after-commit-1"), any())).thenReturn(false);
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(notification)).thenReturn(saved);
        when(notificationMapper.toResponse(saved)).thenReturn(new com.notifyhub.notification.dto.NotificationResponseDTO());

        TransactionSynchronizationManager.initSynchronization();
        try {
            notificationService.sendNotification(request);
            assertEquals(1, TransactionSynchronizationManager.getSynchronizations().size());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventProducer).publish(captor.capture());
        assertEquals(1L, captor.getValue().notificationId());
    }
}
