package com.gopick.server.project.service;

import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.dto.ProjectReadResponse;
import com.gopick.server.project.dto.ProjectResponse;
import com.gopick.server.project.exception.ProjectNotFoundException;
import com.gopick.server.project.exception.ProjectValidationException;
import com.gopick.server.project.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class ProjectReadService {
    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final ProjectSearchRepository search;
    private final ProjectMembershipRepository memberships;
    public ProjectReadService(ProjectAccess access, ProjectRepository projects,
                              ProjectSearchRepository search, ProjectMembershipRepository memberships) {
        this.access = access; this.projects = projects; this.search = search; this.memberships = memberships;
    }
    public List<ProjectResponse> list(Long accountId, String status, String recruiting) {
        access.requireAccount(accountId);
        ProjectStatus parsedStatus = null;
        if (status != null) {
            try { parsedStatus = ProjectStatus.valueOf(status); }
            catch (IllegalArgumentException e) { throw new ProjectValidationException("status", "허용되지 않는 상태입니다."); }
        }
        Boolean parsedRecruiting = null;
        if (recruiting != null) {
            if (recruiting.equals("true")) parsedRecruiting = true;
            else if (recruiting.equals("false")) parsedRecruiting = false;
            else throw new ProjectValidationException("recruiting", "true 또는 false여야 합니다.");
        }
        return search.search(parsedStatus, parsedRecruiting).stream().map(ProjectResponse::from).toList();
    }
    public ProjectReadResponse detail(Long accountId, String projectId) {
        access.requireAccount(accountId);
        Project p = projects.findById(access.parseId(projectId)).orElseThrow(ProjectNotFoundException::new);
        var members = memberships.findMembers(p.getId()).stream().map(m -> {
            var profile = m.getProfile();
            boolean visible = profile.isPublic() || Objects.equals(profile.getAccount().getId(), accountId);
            return new ProjectReadResponse.Member(profile.getId(), visible ? profile.getName() : null, profile.isPublic(),
                    m.getRole(), m.getMemberType(), m.getJoinedAt());
        }).toList();
        return new ProjectReadResponse(p.getId(), p.getTitle(), p.getDescription(), p.getBackground(), p.getGoal(),
                p.getWish(), p.getExtra(), p.getStatus(), p.getStage(), p.getRecruiting(), p.getCreatedAt(),
                p.getStatusUpdatedAt(), p.getStageUpdatedAt(), members);
    }
}
