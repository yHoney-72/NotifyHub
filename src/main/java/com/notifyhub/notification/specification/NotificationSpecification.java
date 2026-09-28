package com.notifyhub.notification.specification;

import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.notification.entity.Notification;
import com.notifyhub.security.entity.AppUser;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

public class NotificationSpecification {

    public static Specification<Notification> hasUser(AppUser user) {
        return (root, query, cb) ->
                user == null ? null : cb.equal(root.get("user"), user);
    }

    public static Specification<Notification> hasStatus(NotificationStatus status) {
        return (root, query, cb) ->
                status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Notification> hasChannel(Channel channel) {
        return (root, query, cb) ->
                channel == null ? null : cb.equal(root.get("channel"), channel);
    }

    public static Specification<Notification> hasRecipient(String recipient) {
        return (root, query, cb) ->
                recipient == null ? null :
                        cb.equal(root.get("recipient"), recipient);
    }

    public static Specification<Notification> createdAtFrom(LocalDateTime from) {
        return (root, query, cb) ->
                from == null ? null :
                        cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Notification> createdAtTo(LocalDateTime to) {
        return (root, query, cb) ->
                to == null ? null :
                        cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}