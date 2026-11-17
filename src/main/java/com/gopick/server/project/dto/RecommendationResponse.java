package com.gopick.server.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gopick.server.project.domain.ProjectRole;

import java.util.List;

public record RecommendationResponse(
        Long projectId,
        ProjectRole role,
        List<Item> items
) {

    public record Item(
            Long profileId,
            String name,
            int rank,
            List<Reason> matched,
            List<Reason> unmatched,

            @JsonInclude(JsonInclude.Include.NON_NULL)
            String summary
    ) {}

    public record Reason(
            Category category,
            String text
    ) {}

    public enum Category {
        STACK,
        COLLAB,
        EXPERIENCE,
        ROLE,
        FIELD,
        PURPOSE,
        MAJOR,
        PART
    }
}