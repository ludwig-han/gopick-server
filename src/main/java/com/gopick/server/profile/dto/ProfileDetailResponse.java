package com.gopick.server.profile.dto;

import com.gopick.server.profile.domain.ProfilePreferenceCategory;

import java.util.List;

public record ProfileDetailResponse(
        Long profileId,
        String name,
        String major,
        String contactPlatform,
        boolean isPublic,
        String teamTendency,
        String intro,
        List<String> parts,
        List<Technology> technologies,
        List<String> interests,
        List<String> preferredRoles,
        List<Preference> preferences,
        List<Experience> experiences
) {
    public record Technology(String technology, Integer level) {}

    public record Preference(ProfilePreferenceCategory category, String value) {}

    public record Experience(Long experienceId, String description, List<Attribute> attributes) {}

    public record Attribute(String category, String value) {}
}
