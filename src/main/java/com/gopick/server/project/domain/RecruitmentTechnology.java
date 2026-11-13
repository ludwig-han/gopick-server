package com.gopick.server.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record RecruitmentTechnology(
        @Column(nullable = false) String technology,
        @Column(nullable = false) int minLevel
) {}
