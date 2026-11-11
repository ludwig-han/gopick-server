package com.gopick.server.project.service;

import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.dto.ProjectResponse;
import com.gopick.server.project.dto.ProjectUpdateRequest;
import com.gopick.server.project.exception.InvalidProjectStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectEditService {
    private final ProjectAccess access;
    public ProjectEditService(ProjectAccess access) { this.access = access; }
    @Transactional
    public ProjectResponse update(Long accountId, String projectId, ProjectUpdateRequest update) {
        var project = access.lock(accountId, projectId);
        access.requireLeader(project.getId(), accountId);
        if (Boolean.TRUE.equals(update.getRecruiting()) && project.getStatus() != ProjectStatus.PREPARING)
            throw new InvalidProjectStateException("공개 모집은 시작 전 프로젝트에서만 가능합니다.");
        if (update.getTitle() != null) project.setTitle(update.getTitle());
        if (update.getDescription() != null) project.setDescription(update.getDescription());
        if (update.getBackground() != null) project.setBackground(update.getBackground());
        if (update.getGoal() != null) project.setGoal(update.getGoal());
        if (update.getWish() != null) project.setWish(update.getWish());
        if (update.getExtra() != null) project.setExtra(update.getExtra());
        if (update.getRecruiting() != null) project.setRecruiting(update.getRecruiting());
        return ProjectResponse.from(project);
    }
}
