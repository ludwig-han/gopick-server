package com.gopick.server.project.dto;

import com.gopick.server.project.domain.ParticipationRequest;
import com.gopick.server.project.domain.ParticipationRequestStatus;
import com.gopick.server.project.domain.ParticipationRequestType;
import com.gopick.server.project.domain.ProjectRole;

import java.time.LocalDateTime;

public record ParticipationRequestResponse(
        Long requestId, Long projectId, String projectTitle, ParticipationRequestType type,
        ProjectRole role, ParticipationRequestStatus status, Long senderProfileId, Long participantProfileId,
        LocalDateTime createdAt, LocalDateTime resolvedAt
) {
    public static ParticipationRequestResponse from(ParticipationRequest request) {
        var project = request.getProject();
        return new ParticipationRequestResponse(request.getId(), project.getId(), project.getTitle(),
                request.getType(), request.getRole(), request.getStatus(), request.getSender().getId(),
                request.getParticipant().getId(), request.getCreatedAt(), request.getResolvedAt());
    }
}
