package com.notifyhub.notification.service;

import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.notification.dto.NotificationRequestDTO;
import com.notifyhub.notification.dto.NotificationResponseDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface NotificationService {
    NotificationResponseDTO sendNotification(NotificationRequestDTO request);
    NotificationResponseDTO getNotification(Long id);

    Page<NotificationResponseDTO> getNotifications(
            String recipient,
            NotificationStatus status,
            Channel channel,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable
    );
}