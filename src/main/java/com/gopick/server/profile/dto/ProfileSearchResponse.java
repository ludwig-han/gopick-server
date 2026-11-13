package com.gopick.server.profile.dto;

import java.util.List;

public record ProfileSearchResponse(
        Long profileId,
        String name,
        String major,
        String intro,
        List<ProfileDetailResponse.Technology> technologies,
        List<String> interests,
        List<String> preferredRoles
) {}
