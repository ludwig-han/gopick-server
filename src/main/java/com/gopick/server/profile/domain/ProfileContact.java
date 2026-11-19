package com.gopick.server.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ProfileContact {
    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "contact_value", nullable = false)
    private String value;

    protected ProfileContact() {}

    public ProfileContact(String platform, String value) {
        this.platform = platform;
        this.value = value;
    }

    public String getPlatform() { return platform; }
    public String getValue() { return value; }
}
