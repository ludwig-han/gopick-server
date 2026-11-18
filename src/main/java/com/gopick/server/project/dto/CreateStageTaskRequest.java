package com.gopick.server.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import com.gopick.server.project.domain.ProjectStage;

public record CreateStageTaskRequest(
        @NotNull ProjectStage stage,
        @NotBlank @Size(max = 255) @Pattern(regexp = "^[^\\r\\n]*$") String text) {}
