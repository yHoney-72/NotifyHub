package com.notifyhub.notification.dto;

import com.notifyhub.enums.Channel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@Schema(description = "Notification request")
public class NotificationRequestDTO {
    @NotNull(message = "Channel is required")
    @Schema(example = "EMAIL")
    private Channel channel;

    @NotBlank(message = "Recipient is required")
    @Schema(example = "user@gmail.com")
    private String recipient;

    @Schema(example = "Welcome to NotifyHub")
    private String rawSubject;

    @Schema(example = "Hello, welcome!")
    private String rawBody;

    @Schema(example = "WELCOME_EMAIL")
    private String templateCode;

    private Map<String, Object> data;

    @NotBlank(message = "Idempotency key is required")
    @Schema(example = "order-123")
    private String idempotencyKey;

    @AssertTrue(message = "Either raw content or templateCode must be provided")
    public boolean isContentOrTemplateProvided() {
        boolean hasRawContent = rawBody != null && !rawBody.isBlank();
        boolean hasTemplate = templateCode != null && !templateCode.isBlank();
        return hasRawContent || hasTemplate;
    }
}