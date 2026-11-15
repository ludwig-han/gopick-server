package com.gopick.server.project.domain;

import com.gopick.server.profile.domain.Profile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Table(indexes = @Index(name = "idx_participation_project_participant_status",
        columnList = "project_id, participant_profile_id, status"))
public class ParticipationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_profile_id", nullable = false, updatable = false)
    private Profile sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_profile_id", nullable = false, updatable = false)
    private Profile participant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ParticipationRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ProjectRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ParticipationRequestStatus status = ParticipationRequestStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    protected ParticipationRequest() {}

    public ParticipationRequest(Project project, Profile sender, Profile participant,
                                ParticipationRequestType type, ProjectRole role) {
        this.project = project;
        this.sender = sender;
        this.participant = participant;
        this.type = type;
        this.role = role;
    }

    @PrePersist
    void recordCreationTime() {
        createdAt = LocalDateTime.now();
    }

    public void resolve(ParticipationRequestStatus status) {
        this.status = status;
        resolvedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public Profile getSender() { return sender; }
    public Profile getParticipant() { return participant; }
    public ParticipationRequestType getType() { return type; }
    public ProjectRole getRole() { return role; }
    public ParticipationRequestStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
}
