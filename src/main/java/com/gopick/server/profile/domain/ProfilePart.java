package com.gopick.server.profile.domain;

import jakarta.persistence.*;

@Entity
public class ProfilePart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(nullable = false)
    private String part;

    protected ProfilePart() {}

    public ProfilePart(Profile profile, String part) {
        this.profile = profile;
        this.part = part;
    }

    public Long getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getPart() {
        return part;
    }
}
