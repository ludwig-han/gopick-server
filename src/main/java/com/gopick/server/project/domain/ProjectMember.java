package com.gopick.server.project.domain;

import com.gopick.server.profile.domain.Profile;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
// 같은 사람이 같은 프로젝트에 두 명 존재할 수 없음
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "profile_id"}))
public class ProjectMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectMemberType memberType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole role;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    protected ProjectMember() {}

    public ProjectMember(Project project, Profile profile, ProjectMemberType memberType, ProjectRole role) {
        this.profile = profile;
        this.project = project;
        this.memberType = memberType;
        this.role = role;
    }

    @PrePersist
    public void prePersist() {
        this.joinedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public Profile getProfile() {
        return profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public ProjectMemberType getMemberType() {
        return memberType;
    }

    public void setMemberType(ProjectMemberType memberType) {
        this.memberType = memberType;
    }

    public ProjectRole getRole() {
        return role;
    }

    public void setRole(ProjectRole role) {
        this.role = role;
    }

    public LocalDateTime getJoinedAt() {
        return joinedAt;
    }
}
