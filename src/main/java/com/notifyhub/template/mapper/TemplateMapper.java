package com.notifyhub.template.mapper;

import com.notifyhub.template.dto.TemplateRequestDTO;
import com.notifyhub.template.dto.TemplateResponseDTO;
import com.notifyhub.template.entity.NotificationTemplate;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TemplateMapper {

    NotificationTemplate toEntity(TemplateRequestDTO request);

    TemplateResponseDTO toResponse(NotificationTemplate template);
}