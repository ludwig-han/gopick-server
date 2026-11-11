package com.gopick.server.project.exception;

public class ProjectAccessDeniedException extends RuntimeException {
    public ProjectAccessDeniedException() { super("프로젝트 작업 권한이 없습니다."); }
}
