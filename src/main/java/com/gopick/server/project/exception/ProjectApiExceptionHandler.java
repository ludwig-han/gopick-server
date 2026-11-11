package com.gopick.server.project.exception;

import com.gopick.server.global.ErrorResponse;
import com.gopick.server.global.ValidationErrorResponse;
import com.gopick.server.project.controller.ProjectApiEndpoint;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = ProjectApiEndpoint.class)
public class ProjectApiExceptionHandler {
    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ErrorResponse> missing(ProjectNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("PROJECT_NOT_FOUND", e.getMessage()));
    }
    @ExceptionHandler(ProjectAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> denied(ProjectAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse("PROJECT_ACCESS_DENIED", e.getMessage()));
    }
    @ExceptionHandler(InvalidProjectStateException.class)
    public ResponseEntity<ErrorResponse> state(InvalidProjectStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse("INVALID_PROJECT_STATE", e.getMessage()));
    }
    @ExceptionHandler(ProjectValidationException.class)
    public ResponseEntity<ValidationErrorResponse> invalid(ProjectValidationException e) {
        return ResponseEntity.badRequest().body(new ValidationErrorResponse(
                "VALIDATION_ERROR", "입력값을 확인해주세요.", Map.of(e.getField(), e.getMessage())));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ValidationErrorResponse> unreadable(HttpMessageNotReadableException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ProjectValidationException validation) return invalid(validation);
        }
        return invalid(new ProjectValidationException("body", "요청 JSON의 필드, 값 또는 형식을 확인해주세요."));
    }
}
