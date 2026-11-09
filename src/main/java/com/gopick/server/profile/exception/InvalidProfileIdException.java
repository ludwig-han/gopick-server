package com.gopick.server.profile.exception;

public class InvalidProfileIdException extends RuntimeException {
    public InvalidProfileIdException() {
        super("profileId는 1 이상의 정수여야 합니다.");
    }
}
