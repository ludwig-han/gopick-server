package com.gopick.server.bookmark.domain;

import com.gopick.server.account.domain.Account;
import com.gopick.server.project.domain.Project;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "project_scrap", uniqueConstraints = @UniqueConstraint(
        name = "uk_project_scrap_owner_target", columnNames = {"account_id", "project_id"}))
public class ProjectScrap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    protected ProjectScrap() {}

    public ProjectScrap(Account account, Project project) {
        this.account = account;
        this.project = project;
    }

    public Project getProject() {
        return project;
    }
}
