package com.gopick.server.notification;

import com.gopick.server.account.domain.Account;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(indexes = @Index(name = "idx_notification_recipient_id", columnList = "recipient_id,id"))
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false, updatable = false)
    private Account recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NotificationType type;

    @Column(nullable = false, updatable = false)
    private Long projectId;
    @Column(nullable = false, updatable = false)
    private String projectTitle;
    @Column(updatable = false)
    private Long requestId;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    protected Notification() {}

    Notification(Account recipient, NotificationType type, Long projectId, String projectTitle, Long requestId) {
        this.recipient = recipient;
        this.type = type;
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.requestId = requestId;
        this.createdAt = LocalDateTime.now();
    }

    void markRead() {
        if (readAt == null) readAt = LocalDateTime.now();
    }

    NotificationResponse response() {
        return new NotificationResponse(id, type, projectId, projectTitle, requestId, createdAt, readAt != null, readAt);
    }
}
