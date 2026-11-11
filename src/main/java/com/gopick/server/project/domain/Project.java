package com.gopick.server.project.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectStatus status;

    @Enumerated(EnumType.STRING)
    private ProjectStage stage;

    @Column(nullable = false)
    private String title;

    private String description;

    private String background;

    private String goal;

    private String wish;

    private String extra;

    @Column(nullable = false)
    private Boolean recruiting;

    private LocalDateTime createdAt;
    private LocalDateTime stageUpdatedAt;
    private LocalDateTime statusUpdatedAt;

    protected Project() {}

    public Project(String extra,
                   String wish,
                   String goal,
                   String background,
                   String description,
                   String title,
                   Boolean recruiting) {
        this.extra = extra;
        this.wish = wish;
        this.goal = goal;
        this.background = background;
        this.description = description;
        this.title = title;
        this.status = ProjectStatus.PREPARING;
        this.recruiting = recruiting;
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.statusUpdatedAt = now;
    }

    // getter
    public Long getId() {
        return id;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getBackground() {
        return background;
    }

    public String getGoal() {
        return goal;
    }

    public String getWish() {
        return wish;
    }

    public String getExtra() {
        return extra;
    }

    public Boolean getRecruiting() {
        return recruiting;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStageUpdatedAt() {
        return stageUpdatedAt;
    }

    public LocalDateTime getStatusUpdatedAt() {
        return statusUpdatedAt;
    }

    public ProjectStage getStage() {
        return stage;
    }

    // setter
    public void setStatus(ProjectStatus status) {
        this.status = status;
        this.statusUpdatedAt = LocalDateTime.now();
    }
    public void setStage(ProjectStage stage) {
        this.stage = stage;
        this.stageUpdatedAt = LocalDateTime.now();
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public void setWish(String wish) {
        this.wish = wish;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }

    public void setRecruiting(Boolean recruiting) {
        this.recruiting = recruiting;
    }
}
