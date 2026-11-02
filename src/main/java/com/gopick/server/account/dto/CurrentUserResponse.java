package com.gopick.server.account.dto;

public class CurrentUserResponse {
    private final Long accountId;
    private final String email;
    private final Long profileId;

    public CurrentUserResponse(Long accountId, String email, Long profileId) {
        this.accountId = accountId;
        this.email = email;
        this.profileId = profileId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getEmail() {
        return email;
    }

    public Long getProfileId() {
        return profileId;
    }
}
