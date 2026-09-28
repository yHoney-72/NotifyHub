package com.notifyhub.template.dto;

import com.notifyhub.enums.Channel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Schema(description = "Notification template response")
public class TemplateResponseDTO {

    @Schema(example = "1")
    private Long id;

    @Schema(example = "WELCOME_EMAIL")
    private String code;

    @Schema(example = "EMAIL")
    private Channel channel;

    @Schema(example = "Welcome to NotifyHub")
    private String subject;

    @Schema(example = "Hello {{name}}, welcome to NotifyHub!")
    private String body;

    private LocalDateTime updatedAt;
}