package com.gopick.server.profile.domain;

import jakarta.persistence.*;

@Entity
public class ProfileTechnology {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(nullable = false)
    private String technology;

    @Column(nullable = false)
    private Integer level;

    protected ProfileTechnology() {}

    public ProfileTechnology(Profile profile, String technology, Integer level) {
        this.profile = profile;
        this.technology = technology;
        this.level = level;
    }

    public Long getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getTechnology() {
        return technology;
    }

    public Integer getLevel() {
        return level;
    }
}