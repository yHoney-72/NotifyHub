package com.notifyhub.template.dto;

import com.notifyhub.enums.Channel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Create notification template request")
public class TemplateRequestDTO {

    @Schema(example = "WELCOME_EMAIL")
    @NotBlank
    private String code;

    @Schema(example = "EMAIL")
    @NotNull
    private Channel channel;

    @Schema(example = "Welcome to NotifyHub")
    private String subject;

    @Schema(example = "Hello {{name}}, welcome to NotifyHub!")
    @NotBlank
    private String body;
}