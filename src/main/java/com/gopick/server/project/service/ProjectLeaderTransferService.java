package com.gopick.server.project.service;

import com.gopick.server.project.domain.ProjectMember;
import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.exception.InvalidProjectStateException;
import com.gopick.server.project.exception.ProjectValidationException;
import com.gopick.server.project.repository.ProjectMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.gopick.server.account.domain.Account;
import com.gopick.server.profile.exception.ProfileNotFoundException;

@Service
public class ProjectLeaderTransferService {
    private final ProjectAccess access;
    private final ProjectMemberRepository members;
    private final EntityManager entityManager;

    public ProjectLeaderTransferService(ProjectAccess access, ProjectMemberRepository members, EntityManager entityManager) {
        this.access = access;
        this.members = members;
        this.entityManager = entityManager;
    }

    @Transactional
    public void transfer(Long accountId, String projectId, Long targetProfileId) {
        var project = access.lock(accountId, projectId);
        ProjectMember leader = access.requireLeader(project.getId(), accountId);
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new InvalidProjectStateException("진행 중인 프로젝트에서만 팀장 권한을 위임할 수 있습니다.");
        }

        ProjectMember target = members.findByProject_IdAndProfile_Id(project.getId(), targetProfileId)
                .filter(member -> member.getMemberType() == ProjectMemberType.MEMBER)
                .orElseThrow(() -> new ProjectValidationException(
                        "targetProfileId", "같은 프로젝트의 팀원에게만 팀장 권한을 위임할 수 있습니다."));

        if (entityManager.find(Account.class, target.getProfile().getAccount().getId(), LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new ProfileNotFoundException();
        }
        leader.setMemberType(ProjectMemberType.MEMBER);
        target.setMemberType(ProjectMemberType.LEADER);
    }
}
