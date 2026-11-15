package com.gopick.server.project.dto;

import com.gopick.server.project.domain.ParticipationRequestStatus;
import jakarta.validation.constraints.NotNull;

public record ResolveParticipationRequest(@NotNull ParticipationRequestStatus status) {}
