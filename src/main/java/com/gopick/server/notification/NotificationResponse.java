package com.gopick.server.notification;
import java.time.LocalDateTime;
public record NotificationResponse(Long id, NotificationType type, Long projectId, String projectTitle, Long requestId, LocalDateTime createdAt, boolean read, LocalDateTime readAt) {}
