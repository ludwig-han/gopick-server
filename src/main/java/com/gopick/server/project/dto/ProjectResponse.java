package com.gopick.server.project.dto;

import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectStage;
import com.gopick.server.project.domain.ProjectStatus;
import java.time.LocalDateTime;

public record ProjectResponse(Long projectId, String title, String description, String background,
                              String goal, String wish, String extra, ProjectStatus lifecycleStatus,
                              ProjectStage stage, Boolean recruiting, LocalDateTime createdAt,
                              LocalDateTime statusUpdatedAt, LocalDateTime stageUpdatedAt) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(p.getId(), p.getTitle(), p.getDescription(), p.getBackground(), p.getGoal(),
                p.getWish(), p.getExtra(), p.getStatus(), p.getStage(), p.getRecruiting(), p.getCreatedAt(),
                p.getStatusUpdatedAt(), p.getStageUpdatedAt());
    }
}
