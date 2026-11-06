package com.gopick.server.profile.exception;

public class DuplicateProfileException extends RuntimeException {
    public DuplicateProfileException() {
        super("이미 해당 계정에 작성된 프로필이 존재합니다.");
    }
}
