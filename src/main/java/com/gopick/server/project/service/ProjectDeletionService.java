package com.gopick.server.project.service;

import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectDeletionService {
    private final ProjectAccess access;
    private final NotificationService notifications;

    public ProjectDeletionService(ProjectAccess access, NotificationService notifications) {
        this.access = access;
        this.notifications = notifications;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long accountId, String projectId) {
        var project = access.lock(accountId, projectId);
        access.requireLeader(project.getId(), accountId);
        notifications.sendToOtherMembers(project, accountId, NotificationType.PROJECT_DELETED);
        project.markDeleted();
    }
}
