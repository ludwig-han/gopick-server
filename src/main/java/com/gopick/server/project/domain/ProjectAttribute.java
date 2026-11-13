package com.gopick.server.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record ProjectAttribute(
        @Enumerated(EnumType.STRING) @Column(nullable = false) ProjectAttributeCategory category,
        @Column(name = "attribute_value", nullable = false) String value
) {}
