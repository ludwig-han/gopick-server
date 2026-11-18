package com.gopick.server.project.exception;

public class StageTaskNotFoundException extends RuntimeException {
    public StageTaskNotFoundException() {
        super("과제를 찾을 수 없습니다.");
    }
}
