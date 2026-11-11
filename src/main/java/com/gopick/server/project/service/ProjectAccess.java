package com.gopick.server.project.service;

import com.gopick.server.account.exception.UnauthenticatedException;
import com.gopick.server.account.repository.AccountRepository;
import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectMember;
import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.exception.*;
import com.gopick.server.project.repository.ProjectLookupRepository;
import com.gopick.server.project.repository.ProjectMembershipRepository;
import org.springframework.stereotype.Component;

@Component
public class ProjectAccess {
    private final AccountRepository accounts;
    private final ProjectLookupRepository projects;
    private final ProjectMembershipRepository memberships;
    public ProjectAccess(AccountRepository accounts, ProjectLookupRepository projects, ProjectMembershipRepository memberships) {
        this.accounts = accounts; this.projects = projects; this.memberships = memberships;
    }
    public void requireAccount(Long accountId) {
        if (accountId == null || !accounts.existsById(accountId)) throw new UnauthenticatedException();
    }
    public Long parseId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id > 0 && raw.matches("[0-9]+")) return id;
        } catch (NumberFormatException ignored) {}
        throw new ProjectValidationException("projectId", "projectId는 1 이상의 정수여야 합니다.");
    }
    public Project lock(Long accountId, String raw) {
        requireAccount(accountId);
        return projects.findForUpdate(parseId(raw)).orElseThrow(ProjectNotFoundException::new);
    }
    public ProjectMember requireMember(Long projectId, Long accountId) {
        return memberships.findMembership(projectId, accountId).orElseThrow(ProjectAccessDeniedException::new);
    }
    public void requireLeader(Long projectId, Long accountId) {
        if (requireMember(projectId, accountId).getMemberType() != ProjectMemberType.LEADER)
            throw new ProjectAccessDeniedException();
    }
}
