package com.gopick.server.profile.domain;

import com.gopick.server.account.domain.Account;
import jakarta.persistence.*;

import java.time.LocalDateTime;


@Entity
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String major;

    @Column(nullable = false)
    private String contactPlatform;

    @Column(nullable = false)
    private String contactId;

    private boolean isPublic;

    private String teamTendency;

    private String intro;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected Profile() {}

    public Profile(
            Account account,
            String name,
            String major,
            String contactPlatform,
            String contactId,
            boolean isPublic,
            String teamTendency,
            String intro
    ) {
        this.account = account;
        this.name = name;
        this.major = major;
        this.contactPlatform = contactPlatform;
        this.contactId = contactId;
        this.isPublic = isPublic;
        this.teamTendency = teamTendency;
        this.intro = intro;
    }

    public void update(
            String name,
            String major,
            String contactPlatform,
            String contactId,
            boolean isPublic,
            String teamTendency,
            String intro
    ) {
        this.name = name;
        this.major = major;
        this.contactPlatform = contactPlatform;
        this.contactId = contactId;
        this.isPublic = isPublic;
        this.teamTendency = teamTendency;
        this.intro = intro;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public String getName() {
        return name;
    }

    public String getMajor() {
        return major;
    }

    public String getContactPlatform() {
        return contactPlatform;
    }

    public String getContactId() {
        return contactId;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public String getTeamTendency() {
        return teamTendency;
    }

    public String getIntro() {
        return intro;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
