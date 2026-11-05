package com.gopick.server.profile.dto;

public class CreateProfileResponse {

    private final Long profileId;

    public CreateProfileResponse(Long profileId) {
        this.profileId = profileId;
    }

    public Long getProfileId() {
        return profileId;
    }
}