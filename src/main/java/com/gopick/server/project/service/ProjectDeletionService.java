package com.gopick.server.project.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectDeletionService {
    private final ProjectAccess access;

    public ProjectDeletionService(ProjectAccess access) {
        this.access = access;
    }

    @Transactional
    public void delete(Long accountId, String projectId) {
        var project = access.lock(accountId, projectId);
        access.requireLeader(project.getId(), accountId);
        project.markDeleted();
    }
}
