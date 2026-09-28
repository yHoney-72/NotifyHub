package com.notifyhub.template.service;

import com.notifyhub.template.dto.TemplateRequestDTO;
import com.notifyhub.template.dto.TemplateResponseDTO;
import com.notifyhub.template.dto.TemplateUpdateRequestDTO;

public interface TemplateService {

    TemplateResponseDTO createTemplate(TemplateRequestDTO request);

    TemplateResponseDTO getTemplateByCode(String code);

    TemplateResponseDTO updateTemplate(String code, TemplateUpdateRequestDTO request);

    void deactivateTemplate(String code);
}