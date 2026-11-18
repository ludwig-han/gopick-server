package com.gopick.server.project.exception;

import org.springframework.http.HttpStatus;

public class AiServiceException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    private AiServiceException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public static AiServiceException unavailable() {
        return new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE",
                "현재 AI 기능을 사용할 수 없습니다.", null);
    }

    public static AiServiceException timeout(Throwable cause) {
        return new AiServiceException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT",
                "AI 응답이 지연됐습니다. 잠시 후 다시 시도해주세요.", cause);
    }

    public static AiServiceException upstreamFailure(Throwable cause) {
        return new AiServiceException(HttpStatus.BAD_GATEWAY, "AI_UPSTREAM_ERROR",
                "AI 결과를 생성하지 못했습니다. 잠시 후 다시 시도해주세요.", cause);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
