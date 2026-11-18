package com.gopick.server.project.service;

import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectActivity;
import com.gopick.server.project.domain.ProjectStage;
import com.gopick.server.project.domain.ProjectStageTask;
import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.dto.ProjectActivityResponse;
import com.gopick.server.project.dto.StageTaskResponse;
import com.gopick.server.project.exception.InvalidProjectStateException;
import com.gopick.server.project.exception.ProjectNotFoundException;
import com.gopick.server.project.exception.StageTaskNotFoundException;
import com.gopick.server.project.repository.ProjectActivityRepository;
import com.gopick.server.project.repository.ProjectStageTaskRepository;
import com.gopick.server.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ProjectWorkLogService {
    private final ProjectAccess access;
    private final ProjectStageTaskRepository tasks;
    private final ProjectActivityRepository activities;
    private final ProjectRepository projects;

    public ProjectWorkLogService(ProjectAccess access, ProjectStageTaskRepository tasks,
                                 ProjectActivityRepository activities, ProjectRepository projects) {
        this.access = access;
        this.tasks = tasks;
        this.activities = activities;
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<StageTaskResponse> listTasks(Long accountId, String projectId) {
        Project project = readableProject(accountId, projectId);
        return tasks.findAllByProject_IdOrderByIdAsc(project.getId())
                .stream().map(StageTaskResponse::from).toList();
    }

    public StageTaskResponse createTask(Long accountId, String projectId, ProjectStage stage, String text) {
        Project project = access.lock(accountId, projectId);
        access.requireMember(project.getId(), accountId);
        requireInProgress(project);
        return StageTaskResponse.from(tasks.save(new ProjectStageTask(project, stage, text.strip())));
    }

    public StageTaskResponse updateTask(Long accountId, String projectId, String rawTaskId, boolean completed) {
        Project project = access.lock(accountId, projectId);
        access.requireMember(project.getId(), accountId);
        requireInProgress(project);
        var task = findTask(project, rawTaskId);
        task.setCompleted(completed);
        return StageTaskResponse.from(task);
    }

    public void deleteTask(Long accountId, String projectId, String rawTaskId) {
        Project project = access.lock(accountId, projectId);
        access.requireMember(project.getId(), accountId);
        requireInProgress(project);
        tasks.delete(findTask(project, rawTaskId));
    }

    @Transactional(readOnly = true)
    public List<ProjectActivityResponse> listActivities(Long accountId, String projectId) {
        Project project = readableProject(accountId, projectId);
        return activities.findActivities(project.getId()).stream().map(ProjectActivityResponse::from).toList();
    }

    public ProjectActivityResponse createActivity(Long accountId, String projectId, String content) {
        Project project = access.lock(accountId, projectId);
        var member = access.requireMember(project.getId(), accountId);
        requireInProgress(project);
        return ProjectActivityResponse.from(activities.save(new ProjectActivity(project, member.getProfile(), content.strip())));
    }

    private ProjectStageTask findTask(Project project, String rawTaskId) {
        return tasks.findByIdAndProject_Id(access.parseId(rawTaskId, "taskId"), project.getId())
                .orElseThrow(StageTaskNotFoundException::new);
    }

    private Project readableProject(Long accountId, String projectId) {
        access.requireAccount(accountId);
        return projects.findById(access.parseId(projectId)).orElseThrow(ProjectNotFoundException::new);
    }

    private static void requireInProgress(Project project) {
        if (project.getStatus() != ProjectStatus.IN_PROGRESS) {
            throw new InvalidProjectStateException("진행 중인 프로젝트에서만 과제와 활동 기록을 변경할 수 있습니다.");
        }
    }
}
