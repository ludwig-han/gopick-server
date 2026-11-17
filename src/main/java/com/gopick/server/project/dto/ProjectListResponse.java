package com.gopick.server.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gopick.server.project.domain.*;
import java.time.LocalDateTime;
import java.util.List;

public record ProjectListResponse(
        Long projectId, String title, String description, String background, String goal, String wish, String extra, String post,
        ProjectStatus lifecycleStatus, ProjectStage stage, Boolean recruiting, LocalDateTime createdAt,
        LocalDateTime statusUpdatedAt, LocalDateTime stageUpdatedAt,
        List<ProjectReadResponse.Member> members, List<ProjectRecruitmentDetails.RoleRecruitment> recruitments,
        List<ProjectRecruitmentDetails.Attribute> attributes,
        @JsonInclude(JsonInclude.Include.NON_NULL) ProjectMemberType myAuthority) {
    public static ProjectListResponse from(Project p, List<ProjectReadResponse.Member> members,
                                           List<ProjectRecruitmentDetails.RoleRecruitment> recruitments,
                                           ProjectMemberType myAuthority) {
        var attributes = p.getAttributes().stream()
                .map(a -> new ProjectRecruitmentDetails.Attribute(a.category(), a.value())).toList();
        return new ProjectListResponse(p.getId(), p.getTitle(), p.getDescription(), p.getBackground(), p.getGoal(),
                p.getWish(), p.getExtra(), p.getPost(), p.getStatus(), p.getStage(), p.getRecruiting(), p.getCreatedAt(),
                p.getStatusUpdatedAt(), p.getStageUpdatedAt(), members, recruitments, attributes, myAuthority);
    }
}
