package com.notifyhub.notification.repository;

import com.notifyhub.notification.entity.Notification;
import com.notifyhub.security.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long>,
        JpaSpecificationExecutor<Notification> {

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    Optional<Notification> findByIdAndUser(Long id, AppUser user);

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKeyAndUser(String idempotencyKey, AppUser user);
}