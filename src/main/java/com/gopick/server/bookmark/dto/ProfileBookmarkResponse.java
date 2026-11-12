package com.gopick.server.bookmark.dto;

import com.gopick.server.profile.domain.Profile;

public record ProfileBookmarkResponse(Long profileId, boolean isPublic, String name) {
    public static ProfileBookmarkResponse from(Profile profile) {
        return new ProfileBookmarkResponse(profile.getId(), profile.isPublic(),
                profile.isPublic() ? profile.getName() : "비공개 프로필");
    }
}
