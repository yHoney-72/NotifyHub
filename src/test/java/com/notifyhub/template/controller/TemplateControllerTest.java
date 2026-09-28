package com.notifyhub.template.controller;

import com.notifyhub.enums.Channel;
import com.notifyhub.template.dto.TemplateUpdateRequestDTO;
import com.notifyhub.template.dto.TemplateResponseDTO;
import com.notifyhub.template.service.TemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TemplateController.class)
@AutoConfigureMockMvc(addFilters = true)
class TemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TemplateService templateService;

    @Test
    @WithMockUser(username = "notifyhub", roles = "USER")
    void shouldCreateTemplate() throws Exception {
        TemplateResponseDTO response = new TemplateResponseDTO();
        response.setCode("welcome");
        response.setChannel(Channel.EMAIL);

        when(templateService.createTemplate(any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/templates")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "code": "welcome",
                              "channel": "EMAIL",
                              "subject": "Welcome",
                              "body": "Hello {{name}}"
                            }
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("welcome"))
                .andExpect(jsonPath("$.channel").value("EMAIL"));
    }

    @Test
    @WithMockUser(username = "notifyhub", roles = "USER")
    void shouldGetTemplate() throws Exception {
        TemplateResponseDTO response = new TemplateResponseDTO();
        response.setCode("welcome");

        when(templateService.getTemplateByCode("welcome"))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/templates/welcome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("welcome"));
    }

    @Test
    @WithMockUser(username = "notifyhub", roles = "USER")
    void shouldUpdateTemplate() throws Exception {
        TemplateResponseDTO response = new TemplateResponseDTO();
        response.setCode("welcome");
        response.setChannel(Channel.SMS);

        when(templateService.updateTemplate(
                eq("welcome"),
                any(TemplateUpdateRequestDTO.class)
        )).thenReturn(response);

        mockMvc.perform(put("/api/v1/templates/welcome")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "channel": "SMS",
                              "subject": "Updated",
                              "body": "Updated body"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("welcome"))
                .andExpect(jsonPath("$.channel").value("SMS"));
    }

    @Test
    @WithMockUser(username = "notifyhub", roles = "USER")
    void shouldDeactivateTemplate() throws Exception {
        doNothing()
                .when(templateService)
                .deactivateTemplate("welcome");

        mockMvc.perform(delete("/api/v1/templates/welcome").with(csrf()))
                .andExpect(status().isNoContent());

        verify(templateService)
                .deactivateTemplate("welcome");
    }
}