package com.gopick.server.account.exception;

public class UnauthenticatedException extends RuntimeException {
    public UnauthenticatedException() {
        super("로그인이 필요합니다.");
    }
}
