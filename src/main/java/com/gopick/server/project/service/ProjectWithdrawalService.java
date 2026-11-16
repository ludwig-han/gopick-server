package com.gopick.server.project.service;

import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.exception.InvalidProjectStateException;
import com.gopick.server.project.repository.ProjectMemberRepository;
import com.gopick.server.project.repository.ProjectRepository;
import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectWithdrawalService {
    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final NotificationService notifications;

    public ProjectWithdrawalService(ProjectAccess access, ProjectRepository projects,
                                    ProjectMemberRepository members, NotificationService notifications) {
        this.access = access;
        this.projects = projects;
        this.members = members;
        this.notifications = notifications;
    }

    // Dependent reads must see changes committed while waiting for the project lock.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(Long accountId, String projectId) {
        var project = access.lock(accountId, projectId);
        var member = access.requireMember(project.getId(), accountId);
        boolean isLeader = member.getMemberType() == ProjectMemberType.LEADER;

        switch (project.getStatus()) {
            case PREPARING -> {
                if (isLeader) {
                    notifications.sendToOtherMembers(project, accountId, NotificationType.PROJECT_DELETED);
                    members.deleteAllByProject_Id(project.getId());
                    projects.delete(project);
                    return;
                }
            }
            case IN_PROGRESS -> {
                if (isLeader) {
                    throw new InvalidProjectStateException("진행 중에는 팀장 권한을 위임한 뒤 하차할 수 있습니다.");
                }
            }
            case COMPLETED -> throw new InvalidProjectStateException("완료된 프로젝트에서는 하차할 수 없습니다.");
        }

        notifications.sendToLeader(project, accountId, NotificationType.MEMBER_LEFT);
        members.delete(member);
    }
}
