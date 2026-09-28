package com.notifyhub.template.controller;

import com.notifyhub.template.dto.TemplateRequestDTO;
import com.notifyhub.template.dto.TemplateResponseDTO;
import com.notifyhub.template.dto.TemplateUpdateRequestDTO;
import com.notifyhub.template.service.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/templates")
@RequiredArgsConstructor
public class TemplateController {

    private final TemplateService templateService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    public TemplateResponseDTO createTemplate(
            @Valid @RequestBody TemplateRequestDTO request) {
        return templateService.createTemplate(request);
    }

    @GetMapping("/{code}")
    @PreAuthorize("hasRole('USER')")
    public TemplateResponseDTO getTemplateByCode(
            @PathVariable String code) {
        return templateService.getTemplateByCode(code);
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasRole('USER')")
    public TemplateResponseDTO updateTemplate(
            @PathVariable String code,
            @Valid @RequestBody TemplateUpdateRequestDTO request) {
        return templateService.updateTemplate(code, request);
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('USER')")
    public void deactivateTemplate(@PathVariable String code) {
        templateService.deactivateTemplate(code);
    }
}