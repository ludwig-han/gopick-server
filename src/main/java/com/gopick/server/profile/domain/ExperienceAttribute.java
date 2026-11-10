package com.gopick.server.profile.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

@Entity
public class ExperienceAttribute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "experience_id", nullable = false)
    private ProfileExperience experience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExperienceAttributeCategory category;

    @Column(nullable = false)
    private String value;

    protected ExperienceAttribute() {}

    public ExperienceAttribute(ProfileExperience experience, ExperienceAttributeCategory category, String value) {
        this.experience = experience;
        this.category = category;
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public ProfileExperience getExperience() {
        return experience;
    }

    public ExperienceAttributeCategory getCategory() {
        return category;
    }

    public void setCategory(ExperienceAttributeCategory category) {
        this.category = category;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
