package com.gopick.server.project.dto;

import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RecruitPostDraftRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 255) String description,
        @Size(max = 20) List<@NotBlank @Size(max = 255) String> fields,
        @Size(max = 20) List<@NotBlank @Size(max = 255) String> purposes,
        @Size(max = 6) List<@NotNull @Valid Recruitment> recruitments,
        @Size(max = 20) List<@NotBlank @Size(max = 255) String> collaborations,
        @Size(max = 255) String background,
        @Size(max = 255) String goal,
        @Size(max = 255) String wish
) {
    public record Recruitment(
            @NotNull ProjectRole role,
            @NotNull @PositiveOrZero Integer remainingCount,
            @NotNull ProjectConditionMode techMode,
            @Size(max = 10) List<@NotNull @Valid Technology> technologies
    ) {}

    public record Technology(
            @NotBlank @Size(max = 255) String technology,
            @NotNull @Min(1) @Max(4) Integer minLevel
    ) {}
}
