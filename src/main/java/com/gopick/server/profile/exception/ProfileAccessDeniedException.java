package com.gopick.server.profile.exception;

public class ProfileAccessDeniedException extends RuntimeException {

    public ProfileAccessDeniedException() {
        super("본인의 프로필만 수정할 수 있습니다.");
    }
}