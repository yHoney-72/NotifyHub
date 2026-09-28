package com.notifyhub.notification.dto;

import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class NotificationHistoryFilterDTO {

    private Channel channel;

    private NotificationStatus status;

    private String recipient;

    private LocalDateTime fromDate;

    private LocalDateTime toDate;

    private int page = 0;

    private int size = 20;
}