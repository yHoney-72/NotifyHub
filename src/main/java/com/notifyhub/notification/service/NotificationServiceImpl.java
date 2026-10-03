package com.notifyhub.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.enums.OutboxStatus;
import com.notifyhub.exception.BadRequestException;
import com.notifyhub.exception.DuplicateNotificationException;
import com.notifyhub.exception.ResourceNotFoundException;
import com.notifyhub.messaging.event.NotificationEvent;
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
import com.notifyhub.notification.specification.NotificationSpecification;
import com.notifyhub.security.entity.AppUser;
import com.notifyhub.security.repository.UserRepository;
import com.notifyhub.template.entity.NotificationTemplate;
import com.notifyhub.template.repository.TemplateRepository;
import com.notifyhub.template.renderer.TemplateRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final TemplateRepository templateRepository;
    private final NotificationMapper notificationMapper;
    private final TemplateRenderer templateRenderer;
    private final IdempotencyService idempotencyService;
    private final UserRepository userRepository;
    private final List<NotificationSender> notificationSenders;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final NotifyHubMetrics metrics;

    @Override
    @Transactional
    public NotificationResponseDTO sendNotification(NotificationRequestDTO request) {
        String idempotencyKey = request.getIdempotencyKey();
        AppUser currentUser = getCurrentUser();

        if (!idempotencyService.tryAcquire(
                currentUser.getId(),
                request.getSourceApplication(),
                idempotencyKey)) {
            throw new DuplicateNotificationException(
                    "Notification already exists with idempotency key: " + idempotencyKey);
        }

        try {
            if (notificationRepository
                    .existsByIdempotencyKeyAndUserAndSourceApplication(
                            idempotencyKey,
                            currentUser,
                            request.getSourceApplication())) {
                throw new DuplicateNotificationException(
                        "Notification already exists with idempotency key: " + idempotencyKey);
            }
        } catch (DuplicateNotificationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DuplicateNotificationException(
                    "Notification already exists with idempotency key: " + idempotencyKey, ex);
        }

        if (notificationSenders.stream()
                .noneMatch(sender -> sender.getChannel() == request.getChannel())) {
            throw new BadRequestException(
                    "Unsupported notification channel: " + request.getChannel());
        }

        String finalContent;
        String subject;

        boolean hasRawBody = request.getRawBody() != null && !request.getRawBody().isBlank();
        boolean hasTemplate = request.getTemplateCode() != null && !request.getTemplateCode().isBlank();

        if (hasRawBody && hasTemplate) {
            throw new BadRequestException("Provide either raw content or template, not both");
        }

        if (hasRawBody) {
            if (request.getRawSubject() == null || request.getRawSubject().isBlank()) {
                throw new BadRequestException("rawSubject is required with rawBody");
            }
            finalContent = request.getRawBody();
            subject = request.getRawSubject();
        } else {
            NotificationTemplate template = templateRepository
                    .findByCodeAndActiveTrue(request.getTemplateCode())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Template not found: " + request.getTemplateCode()));

            subject = template.getSubject();
            finalContent = templateRenderer.render(
                    template.getBody(),
                    request.getData());
        }

        Notification notification = notificationMapper.toEntity(request);
        notification.setUser(currentUser);
        notification.setSourceApplication(request.getSourceApplication());
        notification.setRenderedContent(finalContent);
        notification.setStatus(NotificationStatus.PENDING);

        Notification savedNotification = notificationRepository.save(notification);
        metrics.notificationCreated(request.getChannel());

        NotificationEvent event = new NotificationEvent(
                savedNotification.getId(),
                request.getChannel(),
                request.getRecipient(),
                request.getTemplateCode(),
                request.getData(),
                subject,
                finalContent
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize notification event", ex);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("Notification");
        outboxEvent.setAggregateId(savedNotification.getId());
        outboxEvent.setEventType("NotificationCreated");
        outboxEvent.setPayload(payload);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEvent.setAttempts(0);

        outboxEventRepository.save(outboxEvent);

        return notificationMapper.toResponse(savedNotification);
    }

    @Override
    public NotificationResponseDTO getNotification(Long id) {
        AppUser currentUser = getCurrentUser();

        Notification notification = notificationRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found: " + id));

        return notificationMapper.toResponse(notification);
    }

    @Override
    public Page<NotificationResponseDTO> getNotifications(
            String recipient,
            NotificationStatus status,
            Channel channel,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable) {

        if (pageable.getPageSize() > 100) {
            throw new BadRequestException("Page size cannot exceed 100");
        }

        AppUser currentUser = getCurrentUser();

        Specification<Notification> specification = Specification
                .where(NotificationSpecification.hasUser(currentUser))
                .and(NotificationSpecification.hasRecipient(recipient))
                .and(NotificationSpecification.hasStatus(status))
                .and(NotificationSpecification.hasChannel(channel))
                .and(NotificationSpecification.createdAtFrom(fromDate))
                .and(NotificationSpecification.createdAtTo(toDate));

        return notificationRepository.findAll(specification, pageable)
                .map(notificationMapper::toResponse);
    }

    private AppUser getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
                !authentication.isAuthenticated() ||
                authentication.getName() == null) {
            throw new BadRequestException("Authenticated user is required");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + authentication.getName()));
    }
}