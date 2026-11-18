package com.gopick.server.project.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateStageTaskRequest(@NotNull Boolean done) {}
