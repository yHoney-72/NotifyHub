package com.notifyhub.notification.controller;

import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.notification.dto.NotificationRequestDTO;
import com.notifyhub.notification.dto.NotificationResponseDTO;
import com.notifyhub.notification.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('USER')")
    public NotificationResponseDTO sendNotification(
            @Valid @RequestBody NotificationRequestDTO request) {
        return notificationService.sendNotification(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public NotificationResponseDTO getNotification(
            @PathVariable Long id) {
        return notificationService.getNotification(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public Page<NotificationResponseDTO> getNotifications(
            @RequestParam(required = false) String recipient,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) Channel channel,
            @RequestParam(required = false) LocalDateTime fromDate,
            @RequestParam(required = false) LocalDateTime toDate,
            Pageable pageable) {

        return notificationService.getNotifications(
                recipient,
                status,
                channel,
                fromDate,
                toDate,
                pageable
        );
    }
}