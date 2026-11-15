package com.gopick.server.project.service;

import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import com.gopick.server.project.domain.ProjectStage;
import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.dto.ProjectResponse;
import com.gopick.server.project.exception.InvalidProjectStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProjectProgressService {
    private final ProjectAccess access;
    private final NotificationService notifications;
    public ProjectProgressService(ProjectAccess access, NotificationService notifications) { this.access = access; this.notifications = notifications; }
    public ProjectResponse start(Long accountId, String projectId) {
        var project = access.lock(accountId, projectId);
        access.requireLeader(project.getId(), accountId);
        if (project.getStatus() != ProjectStatus.PREPARING)
            throw new InvalidProjectStateException("시작 전 프로젝트만 시작할 수 있습니다.");
        project.setStatus(ProjectStatus.IN_PROGRESS);
        notifications.sendToOtherMembers(project, accountId, NotificationType.PROJECT_STARTED);
        project.setRecruiting(false);
        return ProjectResponse.from(project);
    }

    public ProjectResponse complete(Long accountId, String projectId) {
        var project = access.lock(accountId, projectId);
        access.requireLeader(project.getId(), accountId);
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new InvalidProjectStateException("진행 중인 프로젝트만 완료할 수 있습니다.");
        }
        project.setStatus(ProjectStatus.COMPLETED);
        notifications.sendToOtherMembers(project, accountId, NotificationType.PROJECT_COMPLETED);
        project.setRecruiting(false);
        return ProjectResponse.from(project);
    }

    public ProjectResponse changeStage(Long accountId, String projectId, ProjectStage stage) {
        var project = access.lock(accountId, projectId);
        access.requireMember(project.getId(), accountId);
        if (project.getStatus() != ProjectStatus.IN_PROGRESS)
            throw new InvalidProjectStateException("진행 중인 프로젝트의 단계만 변경할 수 있습니다.");
        if (project.getStage() != stage) {
            project.setStage(stage);
            notifications.sendToOtherMembers(project, accountId, NotificationType.PROJECT_STAGE_CHANGED);
        }
        return ProjectResponse.from(project);
    }
}
