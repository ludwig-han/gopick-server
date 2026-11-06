package com.gopick.server.profile.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
public class ProfilePreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "profile_id", nullable = false)
    private Profile profile;

    @Column(nullable = false)
    // TODO: ProfilePreference category를 enum으로 변경 (COLLAB, STYLE, STRENGTH, PROJECT, COMM)
    private String category;

    @Column(nullable = false)
    private String value;

    protected ProfilePreference() {}

    public ProfilePreference(Profile profile, String category, String value) {
        this.profile = profile;
        this.category = category;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getCategory() {
        return category;
    }

    public String getValue() {
        return value;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
