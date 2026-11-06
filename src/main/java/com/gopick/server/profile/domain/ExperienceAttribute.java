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

    @Column(nullable = false)
    // TODO: category를 enum으로 변경 (TYPE, FIELD, ROLE, TECHNOLOGY)
    private String category;

    @Column(nullable = false)
    private String value;

    protected ExperienceAttribute() {}

    public ExperienceAttribute(ProfileExperience experience, String category, String value) {
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
