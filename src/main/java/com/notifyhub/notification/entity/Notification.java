package com.notifyhub.notification.entity;

import com.notifyhub.enums.Channel;
import com.notifyhub.enums.NotificationStatus;
import com.notifyhub.security.entity.AppUser;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Channel channel;

    @Column(nullable = false)
    private String recipient;

    /*
     * Optional.
     *
     * Direct-content notification mein ye null hoga.
     * Template-based notification mein template code store hoga.
     */
    @Column(length = 100)
    private String templateCode;

    /*
     * Final message that will actually be delivered.
     *
     * Direct content:
     *     renderedContent = request.content
     *
     * Template:
     *     renderedContent = rendered template
     */
    @Column(columnDefinition = "TEXT")
    private String renderedContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(length = 200)
    private String rawSubject;

    @Column(columnDefinition = "TEXT")
    private String rawBody;
    @Column(length = 500)
    private String failureReason;

    @Column(length = 100)
    private String sourceApplication;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;

    @Version
    private Long version;
}