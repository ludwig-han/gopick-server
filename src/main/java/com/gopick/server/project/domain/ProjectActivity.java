package com.gopick.server.project.domain;

import com.gopick.server.profile.domain.Profile;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(indexes = @Index(name = "idx_project_activity_project_id", columnList = "project_id,id"))
public class ProjectActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_profile_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Profile author;

    @Column(nullable = false, length = 255, updatable = false)
    private String content;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProjectActivity() {}

    public ProjectActivity(Project project, Profile author, String content) {
        this.project = project;
        this.author = author;
        this.content = content;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Profile getAuthor() { return author; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
