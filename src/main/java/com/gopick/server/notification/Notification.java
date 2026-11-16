package com.gopick.server.notification;

import com.gopick.server.account.domain.Account;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, updatable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, updatable = false)
    private Long projectId;
    @Column(nullable = false, updatable = false)
    private String projectTitle;
    @Column(updatable = false)
    private Long requestId;
    @Column(updatable = false)
    private Long actorAccountId;
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    protected Notification() {}

    Notification(Account recipient, NotificationType type, Long projectId, String projectTitle, Long requestId, Long actorAccountId) {
        this.recipient = recipient;
        this.type = type;
        this.projectId = projectId;
        this.projectTitle = projectTitle;
        this.requestId = requestId;
        this.actorAccountId = actorAccountId;
        this.createdAt = LocalDateTime.now();
    }

    void markRead() {
        if (readAt == null) readAt = LocalDateTime.now();
    }

    Long requestId() { return requestId; }
    Long actorAccountId() { return actorAccountId; }

    NotificationResponse response(Boolean requestAvailable, String actorName) {
        return new NotificationResponse(id, type, projectId, projectTitle, requestId, requestAvailable,
                actorName, createdAt, readAt != null, readAt);
    }
}
