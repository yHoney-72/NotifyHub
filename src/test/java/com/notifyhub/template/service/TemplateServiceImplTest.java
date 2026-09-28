package com.notifyhub.template.service;

import com.notifyhub.enums.Channel;
import com.notifyhub.exception.DuplicateTemplateException;
import com.notifyhub.exception.TemplateNotFoundException;
import com.notifyhub.template.dto.TemplateRequestDTO;
import com.notifyhub.template.dto.TemplateResponseDTO;
import com.notifyhub.template.dto.TemplateUpdateRequestDTO;
import com.notifyhub.template.entity.NotificationTemplate;
import com.notifyhub.template.mapper.TemplateMapper;
import com.notifyhub.template.repository.TemplateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TemplateServiceImplTest {

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private TemplateMapper templateMapper;

    @InjectMocks
    private TemplateServiceImpl templateService;

    @Test
    void shouldCreateTemplate() {
        TemplateRequestDTO request = new TemplateRequestDTO();
        request.setCode("welcome");
        request.setChannel(Channel.EMAIL);
        request.setSubject("Welcome");
        request.setBody("Hello {{name}}");

        NotificationTemplate entity = new NotificationTemplate();
        NotificationTemplate saved = new NotificationTemplate();
        TemplateResponseDTO response = new TemplateResponseDTO();

        when(templateRepository.existsByCode("welcome")).thenReturn(false);
        when(templateMapper.toEntity(request)).thenReturn(entity);
        when(templateRepository.save(entity)).thenReturn(saved);
        when(templateMapper.toResponse(saved)).thenReturn(response);

        assertSame(response, templateService.createTemplate(request));

        verify(templateRepository).save(entity);
    }

    @Test
    void shouldRejectDuplicateTemplate() {
        TemplateRequestDTO request = new TemplateRequestDTO();
        request.setCode("welcome");

        when(templateRepository.existsByCode("welcome")).thenReturn(true);

        assertThrows(
                DuplicateTemplateException.class,
                () -> templateService.createTemplate(request)
        );

        verify(templateRepository, never()).save(any());
    }

    @Test
    void shouldGetTemplateByCode() {
        NotificationTemplate template = new NotificationTemplate();
        TemplateResponseDTO response = new TemplateResponseDTO();

        when(templateRepository.findByCode("welcome"))
                .thenReturn(Optional.of(template));
        when(templateMapper.toResponse(template))
                .thenReturn(response);

        assertSame(
                response,
                templateService.getTemplateByCode("welcome")
        );
    }

    @Test
    void shouldThrowWhenTemplateNotFound() {
        when(templateRepository.findByCode("missing"))
                .thenReturn(Optional.empty());

        assertThrows(
                TemplateNotFoundException.class,
                () -> templateService.getTemplateByCode("missing")
        );
    }

    @Test
    void shouldUpdateTemplate() {
        NotificationTemplate template = new NotificationTemplate();
        TemplateUpdateRequestDTO request = new TemplateUpdateRequestDTO();

        request.setChannel(Channel.SMS);
        request.setSubject("Updated");
        request.setBody("Updated body");

        TemplateResponseDTO response = new TemplateResponseDTO();

        when(templateRepository.findByCode("welcome"))
                .thenReturn(Optional.of(template));
        when(templateRepository.save(template))
                .thenReturn(template);
        when(templateMapper.toResponse(template))
                .thenReturn(response);

        assertSame(
                response,
                templateService.updateTemplate("welcome", request)
        );

        assertEquals(Channel.SMS, template.getChannel());
        assertEquals("Updated", template.getSubject());
        assertEquals("Updated body", template.getBody());

        verify(templateRepository).save(template);
    }

    @Test
    void shouldDeactivateTemplate() {
        NotificationTemplate template = new NotificationTemplate();
        template.setActive(true);

        when(templateRepository.findByCode("welcome"))
                .thenReturn(Optional.of(template));

        templateService.deactivateTemplate("welcome");

        assertFalse(template.isActive());
        verify(templateRepository).save(template);
    }
}