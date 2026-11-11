package com.gopick.server.project.dto;

import com.gopick.server.project.domain.ProjectRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProjectCreateRequest {
    @NotBlank
    private String title;

    private String description;

    private String background;

    private String goal;

    private String wish;

    private String extra;

    @NotNull
    private ProjectRole role;

    @NotNull
    private Boolean recruiting;

    protected ProjectCreateRequest() {}

    public ProjectCreateRequest(String title, String description, String background, String goal, String wish, String extra, Boolean recruiting, ProjectRole role) {
        this.title = title;
        this.description = description;
        this.background = background;
        this.goal = goal;
        this.wish = wish;
        this.extra = extra;
        this.recruiting = recruiting;
        this.role = role;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getWish() {
        return wish;
    }

    public void setWish(String wish) {
        this.wish = wish;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public Boolean getRecruiting() {
        return recruiting;
    }

    public void setRecruiting(Boolean recruiting) {
        this.recruiting = recruiting;
    }

    public ProjectRole getRole() {
        return role;
    }

    public void setRole(ProjectRole role) {
        this.role = role;
    }
}
