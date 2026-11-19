package com.gopick.server.profile.exception;

public class ProfileContactConflictException extends RuntimeException {
    public ProfileContactConflictException() {
        super("이미 등록된 연락 수단입니다. 전체 연락처 목록을 수정해 주세요.");
    }
}
