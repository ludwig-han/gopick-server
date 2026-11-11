package com.gopick.server.project.dto;

import com.gopick.server.project.domain.*;
import java.time.LocalDateTime;
import java.util.List;

public record ProjectReadResponse(Long projectId, String title, String description, String background,
                                  String goal, String wish, String extra, ProjectStatus lifecycleStatus,
                                  ProjectStage stage, Boolean recruiting, LocalDateTime createdAt,
                                  LocalDateTime statusUpdatedAt, LocalDateTime stageUpdatedAt, List<Member> members) {
    public record Member(Long profileId, String name, boolean isPublic, ProjectRole role,
                         ProjectMemberType authority, LocalDateTime joinedAt) {}
}
