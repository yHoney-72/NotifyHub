package com.notifyhub.notification.mapper;

import com.notifyhub.notification.dto.NotificationRequestDTO;
import com.notifyhub.notification.dto.NotificationResponseDTO;
import com.notifyhub.notification.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    Notification toEntity(NotificationRequestDTO request);

    NotificationResponseDTO toResponse(Notification notification);
}