package com.gopick.server.profile.exception;

public class PrivateProfileException extends RuntimeException {
    public PrivateProfileException() {
        super("비공개 프로필은 본인만 조회할 수 있습니다.");
    }
}
