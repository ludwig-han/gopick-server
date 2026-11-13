package com.gopick.server.project.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_recruitment_project_role", columnNames = {"project_id", "role"}))
public class Recruitment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ProjectRole role;

    @Column(nullable = false)
    private int remainingCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectConditionMode techMode;

    @ElementCollection
    @CollectionTable(name = "recruitment_technology", joinColumns = @JoinColumn(name = "recruitment_id"))
    @OrderColumn(name = "sort_order")
    private List<RecruitmentTechnology> technologies = new ArrayList<>();

    protected Recruitment() {}

    public Recruitment(Project project, ProjectRole role, int remainingCount, ProjectConditionMode techMode,
                       List<RecruitmentTechnology> technologies) {
        this.project = project;
        this.role = role;
        update(remainingCount, techMode, technologies);
    }

    public ProjectRole getRole() {
        return role;
    }

    public int getRemainingCount() {
        return remainingCount;
    }

    public ProjectConditionMode getTechMode() {
        return techMode;
    }

    public List<RecruitmentTechnology> getTechnologies() {
        return technologies;
    }

    public void update(int remainingCount, ProjectConditionMode techMode, List<RecruitmentTechnology> technologies) {
        this.remainingCount = remainingCount;
        this.techMode = techMode;
        this.technologies.clear();
        this.technologies.addAll(technologies);
    }
}
