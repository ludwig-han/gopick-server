package com.gopick.server.project.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(indexes = @Index(name = "idx_stage_task_project_stage", columnList = "project_id,stage,id"))
public class ProjectStageTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ProjectStage stage;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProjectStageTask() {}

    public ProjectStageTask(Project project, ProjectStage stage, String title) {
        this.project = project;
        this.stage = stage;
        this.title = title;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public ProjectStage getStage() { return stage; }
    public String getTitle() { return title; }
    public boolean isCompleted() { return completed; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCompleted(boolean completed) { this.completed = completed; }
}
