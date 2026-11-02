package com.gopick.server.global;

import java.util.Map;

public class ValidationErrorResponse {
    private final String code;
    private final String message;
    private final Map<String, String> errors;

    public  ValidationErrorResponse(String code, String message,  Map<String, String> errors) {
        this.code = code;
        this.message = message;
        this.errors = errors;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getErrors() {
        return errors;
    }
}
