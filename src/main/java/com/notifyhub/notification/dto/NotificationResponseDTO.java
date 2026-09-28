package com.notifyhub.notification.dto;

import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NotificationResponseDTO {

    private Long id;

    private Channel channel;

    private String recipient;

    private String templateCode;

    private NotificationStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}