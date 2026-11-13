package com.gopick.server.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record ProjectLink(
        @Column(name = "link_type", nullable = false) String type,
        @Column(nullable = false, length = 2048) String url
) {}
