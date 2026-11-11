package com.gopick.server.project.exception;

public class ProjectValidationException extends RuntimeException {
    private final String field;
    public ProjectValidationException(String field, String message) { super(message); this.field = field; }
    public String getField() { return field; }
}
