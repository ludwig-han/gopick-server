package com.gopick.server.project.dto;

import com.gopick.server.project.domain.ProjectActivity;
import java.time.LocalDateTime;

public record ProjectActivityResponse(Long id, String content, String authorName, LocalDateTime createdAt) {
    public static ProjectActivityResponse from(ProjectActivity activity) {
        String name = activity.getAuthor() == null ? "탈퇴한 회원입니다" : activity.getAuthor().getName();
        return new ProjectActivityResponse(activity.getId(), activity.getContent(), name, activity.getCreatedAt());
    }
}
