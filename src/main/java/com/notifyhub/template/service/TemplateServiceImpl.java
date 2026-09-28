package com.notifyhub.template.service;

import com.notifyhub.exception.DuplicateTemplateException;
import com.notifyhub.exception.ResourceNotFoundException;
import com.notifyhub.exception.TemplateNotFoundException;
import com.notifyhub.template.dto.TemplateRequestDTO;
import com.notifyhub.template.dto.TemplateResponseDTO;
import com.notifyhub.template.dto.TemplateUpdateRequestDTO;
import com.notifyhub.template.entity.NotificationTemplate;
import com.notifyhub.template.mapper.TemplateMapper;
import com.notifyhub.template.repository.TemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {
    private final TemplateRepository templateRepository;
    private final TemplateMapper templateMapper;
    @Override
    public TemplateResponseDTO createTemplate(TemplateRequestDTO request) {
        if (templateRepository.existsByCode(request.getCode())) {
            throw new DuplicateTemplateException(
                    "Template already exists: " + request.getCode()
            );
        }
        NotificationTemplate template = templateMapper.toEntity(request);
        NotificationTemplate savedTemplate =templateRepository.save(template);
        return templateMapper.toResponse(savedTemplate);
    }
    @Override
    public TemplateResponseDTO getTemplateByCode(String code) {
        NotificationTemplate template =templateRepository.findByCode(code)
                                 .orElseThrow(() ->new TemplateNotFoundException("Template not found: " + code));

        return templateMapper.toResponse(template);
    }

    @Override
    public TemplateResponseDTO updateTemplate(String code,TemplateUpdateRequestDTO request) {
        NotificationTemplate template =templateRepository.findByCode(code)
                        .orElseThrow(() ->new TemplateNotFoundException("Template not found: " + code));
        template.setChannel(request.getChannel());
        template.setSubject(request.getSubject());
        template.setBody(request.getBody());
        NotificationTemplate updatedTemplate =templateRepository.save(template);
        return templateMapper.toResponse(updatedTemplate);
    }

    @Override
    public void deactivateTemplate(String code) {
        NotificationTemplate template =templateRepository.findByCode(code)
                        .orElseThrow(() ->new TemplateNotFoundException("Template not found: " + code));
        template.setActive(false);
        templateRepository.save(template);
    }
}