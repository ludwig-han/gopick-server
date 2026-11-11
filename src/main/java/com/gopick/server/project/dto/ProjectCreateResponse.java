package com.gopick.server.project.dto;

public class ProjectCreateResponse {
    private Long projectId;

    public ProjectCreateResponse(Long projectId) {
        this.projectId = projectId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
}
