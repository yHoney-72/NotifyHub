package com.notifyhub.template.dto;

import com.notifyhub.enums.Channel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Update notification template request")
public class TemplateUpdateRequestDTO {

    @Schema(example = "EMAIL")
    @NotNull
    private Channel channel;

    @Schema(example = "Welcome to NotifyHub")
    private String subject;

    @Schema(example = "Hello {{name}}, welcome back!")
    @NotBlank
    private String body;
}