package com.gopick.server.project.dto;

import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectRole;

import java.util.List;

public record RecruitPostDraftRequest(
        String title,
        String description,
        List<String> fields,
        List<String> purposes,
        List<Recruitment> recruitments,
        List<String> collaborations,
        String background,
        String goal,
        String wish
) {
    public record Recruitment(
            ProjectRole role,
            Integer remainingCount,
            ProjectConditionMode techMode,
            List<Technology> technologies
    ) {}

    public record Technology(
            String technology,
            Integer minLevel
    ) {}
}