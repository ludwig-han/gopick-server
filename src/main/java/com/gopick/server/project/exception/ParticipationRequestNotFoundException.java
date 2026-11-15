package com.gopick.server.project.exception;

public class ParticipationRequestNotFoundException extends RuntimeException {
    public ParticipationRequestNotFoundException() {
        super("참여 요청을 찾을 수 없습니다.");
    }
}
