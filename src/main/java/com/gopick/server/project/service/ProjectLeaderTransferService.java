package com.gopick.server.project.service;

import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import com.gopick.server.project.domain.ProjectMember;
import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.exception.InvalidProjectStateException;
import com.gopick.server.project.exception.ProjectValidationException;
import com.gopick.server.project.repository.ProjectMemberRepository;
import com.gopick.server.profile.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectLeaderTransferService {
    private final ProjectAccess access;
    private final ProjectMemberRepository members;
    private final NotificationService notifications;
    private final ProfileRepository profiles;
    private final ParticipantAccountLock accountLock;

    public ProjectLeaderTransferService(ProjectAccess access, ProjectMemberRepository members,
                                        NotificationService notifications, ProfileRepository profiles,
                                        ParticipantAccountLock accountLock) {
        this.access = access;
        this.members = members;
        this.notifications = notifications;
        this.profiles = profiles;
        this.accountLock = accountLock;
    }

    @Transactional
    public void transfer(Long accountId, String projectId, Long targetProfileId) {
        access.requireAccount(accountId);
        access.parseId(projectId);
        if (targetProfileId != null) profiles.findAccountId(targetProfileId).ifPresent(accountLock::lockIfPresent);
        var project = access.lock(accountId, projectId);
        ProjectMember leader = access.requireLeader(project.getId(), accountId);
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new InvalidProjectStateException("진행 중인 프로젝트에서만 팀장 권한을 위임할 수 있습니다.");
        }

        ProjectMember target = members.findByProject_IdAndProfile_Id(project.getId(), targetProfileId)
                .filter(member -> member.getMemberType() == ProjectMemberType.MEMBER)
                .orElseThrow(() -> new ProjectValidationException(
                        "targetProfileId", "같은 프로젝트의 팀원에게만 팀장 권한을 위임할 수 있습니다."));

        leader.setMemberType(ProjectMemberType.MEMBER);
        target.setMemberType(ProjectMemberType.LEADER);
        notifications.send(target.getProfile().getAccount(), NotificationType.LEADER_TRANSFERRED, project, null);
    }
}
