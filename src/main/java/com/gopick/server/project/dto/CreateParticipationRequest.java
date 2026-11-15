package com.gopick.server.project.dto;

import com.gopick.server.project.domain.ParticipationRequestType;
import com.gopick.server.project.domain.ProjectRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateParticipationRequest(
        @NotNull ParticipationRequestType type,
        @NotNull ProjectRole role,
        @Positive Long targetProfileId
) {}
