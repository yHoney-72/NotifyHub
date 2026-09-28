package com.notifyhub.service;
import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.exception.BadRequestException;
import com.notifyhub.exception.DuplicateNotificationException;
import com.notifyhub.exception.MissingTemplateDataException;
import com.notifyhub.exception.ResourceNotFoundException;
import com.notifyhub.messaging.event.NotificationEvent;
import com.notifyhub.messaging.producer.NotificationEventProducer;
import com.notifyhub.notification.dto.NotificationRequestDTO;
import com.notifyhub.notification.dto.NotificationResponseDTO;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.notification.idempotency.IdempotencyService;
import com.notifyhub.notification.mapper.NotificationMapper;
import com.notifyhub.notification.repository.NotificationRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.context.TestSecurityContextHolder.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationEventProducer notificationEventProducer;
    @Mock private TemplateRepository templateRepository;
    @Mock private NotificationMapper notificationMapper;
    @Mock private TemplateRenderer templateRenderer;
    @Mock private IdempotencyService idempotencyService;
    @Mock private UserRepository userRepository;
    private NotificationServiceImpl notificationService;

    private AppUser currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new AppUser("notifyhub", "encoded-pass", "USER");
        currentUser.setId(10L);
        when(userRepository.findByUsername("notifyhub")).thenReturn(Optional.of(currentUser));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        "notifyhub", "pw", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        com.notifyhub.notification.sender.NotificationSender sender = mock(com.notifyhub.notification.sender.NotificationSender.class);
        lenient().when(sender.getChannel()).thenReturn(Channel.EMAIL);
        notificationService = new NotificationServiceImpl(
                notificationRepository,
                notificationEventProducer,
                templateRepository,
                notificationMapper,
                templateRenderer,
                idempotencyService,
                userRepository,
                java.util.List.of(sender)
        );
    }

    @Test
    void shouldCreateNotificationSuccessfully() {
        NotificationRequestDTO request=new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("honey@gmail.com");
        request.setTemplateCode("honey_welcome_mail");
        request.setData(Map.of("name","Honey","company","NotifyHub"));
        request.setIdempotencyKey("ABC-124");

        NotificationTemplate template=new NotificationTemplate();
        template.setCode("honey_welcome_mail");
        template.setSubject("Welcome to NotifyHub");
        template.setBody("Hi {{name}}, welcome to {{company}}!");

        Notification notification=new Notification();
        Notification savedNotification=new Notification();
        savedNotification.setId(1L);
        savedNotification.setStatus(NotificationStatus.PENDING);
        savedNotification.setRenderedContent("Hi Honey, welcome to NotifyHub!");
        NotificationResponseDTO response=new NotificationResponseDTO();

        when(idempotencyService.tryAcquire("ABC-124")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUser("ABC-124", currentUser)).thenReturn(false);
        when(templateRepository.findByCodeAndActiveTrue("honey_welcome_mail")).thenReturn(Optional.of(template));
        when(templateRenderer.render(template.getBody(),request.getData())).thenReturn("Hi Honey, welcome to NotifyHub!");
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(notification)).thenReturn(savedNotification);
        when(notificationMapper.toResponse(savedNotification)).thenReturn(response);

        NotificationResponseDTO result=notificationService.sendNotification(request);

        assertNotNull(result);
        assertEquals(NotificationStatus.PENDING,savedNotification.getStatus());
        assertEquals("Hi Honey, welcome to NotifyHub!",savedNotification.getRenderedContent());
        verify(notificationRepository).save(notification);
        verify(templateRenderer).render(template.getBody(),request.getData());

        ArgumentCaptor<NotificationEvent> eventCaptor=ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationEventProducer).publish(eventCaptor.capture());
        NotificationEvent event=eventCaptor.getValue();

        assertEquals(1L,event.notificationId());
        assertEquals(Channel.EMAIL,event.channel());
        assertEquals("honey@gmail.com",event.recipient());
        assertEquals("honey_welcome_mail",event.templateCode());
        assertEquals("Welcome to NotifyHub",event.subject());
        assertEquals("Hi Honey, welcome to NotifyHub!",event.renderedContent());
        verify(notificationMapper).toResponse(savedNotification);
    }

    @Test
    void shouldThrowExceptionWhenIdempotencyKeyAlreadyExists() {
        NotificationRequestDTO request=new NotificationRequestDTO();
        request.setIdempotencyKey("ABC-124");

        when(idempotencyService.tryAcquire("ABC-124")).thenReturn(false);

        assertThrows(DuplicateNotificationException.class,()->notificationService.sendNotification(request));
        verify(notificationRepository,never()).existsByIdempotencyKeyAndUser(anyString(), any());
        verify(templateRepository,never()).findByCodeAndActiveTrue(anyString());
        verify(notificationRepository,never()).save(any());
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void shouldThrowExceptionWhenTemplateDoesNotExist() {
        NotificationRequestDTO request=new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setTemplateCode("unknown_template");
        request.setIdempotencyKey("ABC-125");

        when(idempotencyService.tryAcquire("ABC-125")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUser("ABC-125", currentUser)).thenReturn(false);
        when(templateRepository.findByCodeAndActiveTrue("unknown_template")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,()->notificationService.sendNotification(request));
        verify(notificationRepository,never()).save(any());
        verify(templateRenderer,never()).render(anyString(),anyMap());
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void shouldThrowExceptionWhenTemplateDataIsMissing() {
        NotificationRequestDTO request=new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setTemplateCode("honey_welcome_mail");
        request.setIdempotencyKey("ABC-126");
        Map<String,Object> data=Map.of("name","Honey");
        request.setData(data);

        NotificationTemplate template=new NotificationTemplate();
        template.setCode("honey_welcome_mail");
        template.setBody("Hi {{name}}, welcome to {{company}}!");

        when(idempotencyService.tryAcquire("ABC-126")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUser("ABC-126", currentUser)).thenReturn(false);
        when(templateRepository.findByCodeAndActiveTrue("honey_welcome_mail")).thenReturn(Optional.of(template));
        when(templateRenderer.render(template.getBody(),data)).thenThrow(new MissingTemplateDataException("Missing template data: company"));

        assertThrows(MissingTemplateDataException.class,()->notificationService.sendNotification(request));
        verify(notificationRepository,never()).save(any());
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void shouldCreateRawNotificationSuccessfully() {
        NotificationRequestDTO request=new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("raw@gmail.com");
        request.setRawSubject("Raw Subject");
        request.setRawBody("Raw notification body");
        request.setIdempotencyKey("RAW-001");

        Notification notification=new Notification();
        Notification saved=new Notification();
        saved.setId(2L);
        saved.setStatus(NotificationStatus.PENDING);
        saved.setRenderedContent("Raw notification body");
        NotificationResponseDTO response=new NotificationResponseDTO();

        when(idempotencyService.tryAcquire("RAW-001")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUser("RAW-001", currentUser)).thenReturn(false);
        when(notificationMapper.toEntity(request)).thenReturn(notification);
        when(notificationRepository.save(notification)).thenReturn(saved);
        when(notificationMapper.toResponse(saved)).thenReturn(response);

        NotificationResponseDTO result=notificationService.sendNotification(request);

        assertNotNull(result);
        assertEquals("Raw notification body",saved.getRenderedContent());
        verify(templateRepository,never()).findByCodeAndActiveTrue(anyString());
        verify(templateRenderer,never()).render(anyString(),anyMap());
        verify(notificationEventProducer).publish(any());
    }

    @Test
    void shouldRejectRawBodyWithoutSubject() {
        NotificationRequestDTO request=new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("raw@gmail.com");
        request.setRawBody("Raw body");
        request.setIdempotencyKey("RAW-002");

        when(idempotencyService.tryAcquire("RAW-002")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUser("RAW-002", currentUser)).thenReturn(false);

        assertThrows(BadRequestException.class,()->notificationService.sendNotification(request));
        verify(notificationRepository,never()).save(any());
        verifyNoInteractions(notificationEventProducer);
    }

    @Test
    void shouldRejectRawContentAndTemplateTogether() {
        NotificationRequestDTO request=new NotificationRequestDTO();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("raw@gmail.com");
        request.setRawSubject("Raw Subject");
        request.setRawBody("Raw body");
        request.setTemplateCode("WELCOME_EMAIL");
        request.setIdempotencyKey("RAW-003");

        when(idempotencyService.tryAcquire("RAW-003")).thenReturn(true);
        when(notificationRepository.existsByIdempotencyKeyAndUser("RAW-003", currentUser)).thenReturn(false);

        assertThrows(BadRequestException.class,()->notificationService.sendNotification(request));
        verify(notificationRepository,never()).save(any());
        verifyNoInteractions(notificationEventProducer);
    }
}