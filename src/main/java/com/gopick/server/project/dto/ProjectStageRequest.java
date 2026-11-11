package com.gopick.server.project.dto;

import com.gopick.server.project.domain.ProjectStage;
import jakarta.validation.constraints.NotNull;

public record ProjectStageRequest(@NotNull ProjectStage stage) {}
