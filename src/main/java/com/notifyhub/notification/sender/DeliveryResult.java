package com.notifyhub.notification.sender;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeliveryResult {

    private boolean success;

    private String providerMessageId;

    private String errorMessage;
}