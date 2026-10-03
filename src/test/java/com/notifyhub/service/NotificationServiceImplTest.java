package com.notifyhub.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.enums.OutboxStatus;
import com.notifyhub.exception.BadRequestException;
import com.notifyhub.exception.DuplicateNotificationException;
import com.notifyhub.exception.MissingTemplateDataException;
import com.notifyhub.exception.ResourceNotFoundException;
import com.notifyhub.messaging.outbox.OutboxEvent;
import com.notifyhub.messaging.outbox.OutboxEventRepository;
import com.notifyhub.monitoring.NotifyHubMetrics;
import com.notifyhub.notification.dto.NotificationRequestDTO;
import com.notifyhub.notification.dto.NotificationResponseDTO;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.notification.idempotency.IdempotencyService;
import com.notifyhub.notification.mapper.NotificationMapper;
import com.notifyhub.notification.repository.NotificationRepository;
import com.notifyhub.notification.sender.NotificationSender;
import com.notifyhub.notification.service.NotificationServiceImpl;
import com.notifyhub.security.entity.AppUser;
import com.notifyhub.security.repository.UserRepository;
import com.notifyhub.template.entity.NotificationTemplate;
import com.notifyhub.template.repository.TemplateRepository;
import com.notifyhub.template.renderer.TemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {
    @Mock private NotificationRepository notificationRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private TemplateRenderer templateRenderer;
    @Mock private IdempotencyService idempotencyService;
    @Mock private UserRepository userRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private NotifyHubMetrics metrics;

    private NotificationServiceImpl notificationService;
    private AppUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new AppUser("notifyhub", "encoded-pass", "USER");
        currentUser.setId(10L);

        when(userRepository.findByUsername("notifyhub"))
                .thenReturn(Optional.of(currentUser));

        org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .setAuthentication(
                        new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                "notifyhub",
                                "pw",
                                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
                        )
                );

        NotificationSender sender = mock(NotificationSender.class);
        lenient().when(sender.getChannel()).thenReturn(Channel.EMAIL);

        notificationService = new NotificationServiceImpl(
                notificationRepository,
                templateRepository,
                notificationMapper,
                templateRenderer,
                idempotencyService,
                userRepository,
                List.of(sender),
                outboxEventRepository,
                new ObjectMapper(),
                metrics
        );
    }

    @Test
    void shouldCreateNotificationSuccessfully() {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("honey@gmail.com");
        request.setTemplateCode("honey_welcome_mail");
        request.setData(Map.of("name", "Honey", "company", "NotifyHub"));
        request.setIdempotencyKey("ABC-124");
        request.setSourceApplication("orders-service");

        NotificationTemplate template = new NotificationTemplate();
        template.setCode("honey_welcome_mail");
        template.setSubject("Welcome to NotifyHub");
        template.setBody("Hi {{name}}, welcome to {{company}}!");

        Notification notification = new Notification();
        Notification savedNotification = new Notification();
        savedNotification.setId(1L);
        savedNotification.setStatus(NotificationStatus.PENDING);
        savedNotification.setRenderedContent("Hi Honey, welcome to NotifyHub!");
        NotificationResponseDTO response = new NotificationResponseDTO();

        when(idempotencyService.tryAcquire(10L, "orders-service", "ABC-124")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUserAndSourceApplication(
                "ABC-124", currentUser, "orders-service"))
                .thenReturn(false);
        when(templateRepository.findByCodeAndActiveTrue("honey_welcome_mail"))
                .thenReturn(Optional.of(template));
        when(templateRenderer.render(template.getBody(), request.getData()))
                .thenReturn("Hi Honey, welcome to NotifyHub!");
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(notification)).thenReturn(savedNotification);
        when(notificationMapper.toResponse(savedNotification)).thenReturn(response);

        NotificationResponseDTO result = notificationService.sendNotification(request);

        assertNotNull(result);
        assertEquals(NotificationStatus.PENDING, savedNotification.getStatus());
        assertEquals("Hi Honey, welcome to NotifyHub!",
                savedNotification.getRenderedContent());

        verify(notificationRepository).save(notification);
        verify(templateRenderer).render(template.getBody(), request.getData());

        ArgumentCaptor<OutboxEvent> outboxCaptor =
                ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(outboxCaptor.capture());

        OutboxEvent outboxEvent = outboxCaptor.getValue();

        assertEquals("Notification", outboxEvent.getAggregateType());
        assertEquals(1L, outboxEvent.getAggregateId());
        assertEquals("NotificationCreated", outboxEvent.getEventType());
        assertEquals(OutboxStatus.PENDING, outboxEvent.getStatus());
        assertNotNull(outboxEvent.getPayload());

        verify(notificationMapper).toResponse(savedNotification);
    }

    @Test
    void shouldThrowExceptionWhenIdempotencyKeyAlreadyExists() {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setIdempotencyKey("ABC-124");
        request.setSourceApplication("orders-service");

        when(idempotencyService.tryAcquire(10L, "orders-service", "ABC-124")).thenReturn(false);

        assertThrows(
                DuplicateNotificationException.class,
                () -> notificationService.sendNotification(request)
        );

        verify(notificationRepository, never())
                .existsByIdempotencyKeyAndUserAndSourceApplication(anyString(), any(), anyString());

        verify(templateRepository, never())
                .findByCodeAndActiveTrue(anyString());

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void shouldAllowSameUserAndKeyFromDifferentSourceApplication() {
        NotificationRequestDTO request = rawRequest("SCOPED-001", "another-app");
        Notification notification = new Notification();
        Notification savedNotification = new Notification();
        savedNotification.setId(3L);
        NotificationResponseDTO response = new NotificationResponseDTO();

        when(idempotencyService.tryAcquire(10L, "another-app", "SCOPED-001"))
                .thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUserAndSourceApplication(
                "SCOPED-001", currentUser, "another-app"))
                .thenReturn(false);
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(notification)).thenReturn(savedNotification);
        when(notificationMapper.toResponse(savedNotification)).thenReturn(response);

        assertSame(response, notificationService.sendNotification(request));

        verify(notificationRepository).save(notification);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldAllowSameSourceAndKeyForDifferentUser() {
        AppUser secondUser = new AppUser("second-user", "encoded-pass", "USER");
        secondUser.setId(11L);
        when(userRepository.findByUsername("notifyhub"))
                .thenReturn(Optional.of(secondUser));

        NotificationRequestDTO request = rawRequest("SCOPED-002", "shared-app");
        Notification notification = new Notification();
        Notification savedNotification = new Notification();
        savedNotification.setId(4L);
        NotificationResponseDTO response = new NotificationResponseDTO();

        when(idempotencyService.tryAcquire(11L, "shared-app", "SCOPED-002"))
                .thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUserAndSourceApplication(
                "SCOPED-002", secondUser, "shared-app"))
                .thenReturn(false);
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(notification)).thenReturn(savedNotification);
        when(notificationMapper.toResponse(savedNotification)).thenReturn(response);

        assertSame(response, notificationService.sendNotification(request));

        verify(notificationRepository).save(notification);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldThrowExceptionWhenTemplateDoesNotExist() {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setTemplateCode("unknown_template");
        request.setIdempotencyKey("ABC-125");
        request.setSourceApplication("orders-service");

        when(idempotencyService.tryAcquire(10L, "orders-service", "ABC-125")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUserAndSourceApplication(
                "ABC-125", currentUser, "orders-service")).thenReturn(false);
        when(templateRepository.findByCodeAndActiveTrue("unknown_template"))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> notificationService.sendNotification(request)
        );

        verify(notificationRepository, never()).save(any());
        verify(templateRenderer, never()).render(anyString(), anyMap());
        verifyNoInteractions(outboxEventRepository);
    }

    private NotificationRequestDTO rawRequest(String idempotencyKey, String sourceApplication) {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("scoped@example.com");
        request.setRawSubject("Scoped subject");
        request.setRawBody("Scoped body");
        request.setIdempotencyKey(idempotencyKey);
        request.setSourceApplication(sourceApplication);
        return request;
    }

    @Test
    void shouldThrowExceptionWhenTemplateDataIsMissing() {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setTemplateCode("honey_welcome_mail");
        request.setIdempotencyKey("ABC-126");
        request.setSourceApplication("orders-service");

        Map<String, Object> data = Map.of("name", "Honey");
        request.setData(data);

        NotificationTemplate template = new NotificationTemplate();
        template.setCode("honey_welcome_mail");
        template.setBody("Hi {{name}}, welcome to {{company}}!");

        when(idempotencyService.tryAcquire(10L, "orders-service", "ABC-126")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUserAndSourceApplication(
                "ABC-126", currentUser, "orders-service")).thenReturn(false);
        when(templateRepository.findByCodeAndActiveTrue("honey_welcome_mail"))
                .thenReturn(Optional.of(template));
        when(templateRenderer.render(template.getBody(), data))
                .thenThrow(new MissingTemplateDataException(
                        "Missing template data: company"
                ));

        assertThrows(
                MissingTemplateDataException.class,
                () -> notificationService.sendNotification(request)
        );

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void shouldCreateRawNotificationSuccessfully() {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("raw@gmail.com");
        request.setRawSubject("Raw Subject");
        request.setRawBody("Raw notification body");
        request.setIdempotencyKey("RAW-001");
        request.setSourceApplication("orders-service");

        Notification notification = new Notification();
        Notification saved = new Notification();
        saved.setId(2L);
        saved.setStatus(NotificationStatus.PENDING);
        saved.setRenderedContent("Raw notification body");
        NotificationResponseDTO response = new NotificationResponseDTO();

        when(idempotencyService.tryAcquire(10L, "orders-service", "RAW-001")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUserAndSourceApplication(
                "RAW-001", currentUser, "orders-service")).thenReturn(false);
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(notification)).thenReturn(saved);
        when(notificationMapper.toResponse(saved)).thenReturn(response);

        NotificationResponseDTO result =
                notificationService.sendNotification(request);

        assertNotNull(result);
        assertEquals("Raw notification body", saved.getRenderedContent());

        verify(templateRepository, never())
                .findByCodeAndActiveTrue(anyString());

        verify(templateRenderer, never())
                .render(anyString(), anyMap());

        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    void shouldRejectRawBodyWithoutSubject() {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("raw@gmail.com");
        request.setRawBody("Raw body");
        request.setIdempotencyKey("RAW-002");
        request.setSourceApplication("orders-service");

        when(idempotencyService.tryAcquire(10L, "orders-service", "RAW-002")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUserAndSourceApplication(
                "RAW-002", currentUser, "orders-service")).thenReturn(false);

        assertThrows(
                BadRequestException.class,
                () -> notificationService.sendNotification(request)
        );

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(outboxEventRepository);
    }

    @Test
    void shouldRejectRawContentAndTemplateTogether() {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("raw@gmail.com");
        request.setRawSubject("Raw Subject");
        request.setRawBody("Raw body");
        request.setTemplateCode("WELCOME_EMAIL");
        request.setIdempotencyKey("RAW-003");
        request.setSourceApplication("orders-service");

        when(idempotencyService.tryAcquire(10L, "orders-service", "RAW-003")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUserAndSourceApplication(
                "RAW-003", currentUser, "orders-service")).thenReturn(false);

        assertThrows(
                BadRequestException.class,
                () -> notificationService.sendNotification(request)
        );

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(outboxEventRepository);
    }
    @Test
    void shouldPropagateExceptionWhenNotificationSaveFails() {
        NotificationRequestDTO request = new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("failure@gmail.com");
        request.setRawSubject("Failure Test");
        request.setRawBody("Database failure test");
        request.setIdempotencyKey("DB-FAIL-001");
        request.setSourceApplication("NotifyHub-Test");

        Notification notification = new Notification();

        when(idempotencyService.tryAcquire(
                eq(currentUser.getId()),
                eq("NotifyHub-Test"),
                eq("DB-FAIL-001")
        )).thenReturn(true);

        when(notificationRepository.existsByIdempotencyKeyAndUserAndSourceApplication(
                eq("DB-FAIL-001"),
                eq(currentUser),
                eq("NotifyHub-Test")
        )).thenReturn(false);

        when(notificationMapper.toEntity(request))
                .thenReturn(notification);

        when(notificationRepository.save(notification))
                .thenThrow(new RuntimeException("PostgreSQL unavailable"));

        assertThrows(
                RuntimeException.class,
                () -> notificationService.sendNotification(request)
        );

        verify(outboxEventRepository, never())
                .save(any(OutboxEvent.class));
    }
}