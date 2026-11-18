package com.gopick.server.project.dto;

import com.gopick.server.project.domain.ProjectStage;
import com.gopick.server.project.domain.ProjectStageTask;

public record StageTaskResponse(Long id, ProjectStage stage, String text, boolean done) {
    public static StageTaskResponse from(ProjectStageTask task) {
        return new StageTaskResponse(task.getId(), task.getStage(), task.getTitle(), task.isCompleted());
    }
}
