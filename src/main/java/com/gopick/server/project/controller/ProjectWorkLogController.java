package com.gopick.server.project.controller;

import com.gopick.server.project.dto.CreateProjectActivityRequest;
import com.gopick.server.project.dto.CreateStageTaskRequest;
import com.gopick.server.project.dto.ProjectActivityResponse;
import com.gopick.server.project.dto.StageTaskResponse;
import com.gopick.server.project.dto.UpdateStageTaskRequest;
import com.gopick.server.project.service.ProjectWorkLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
@RequestMapping("/projects/{projectId}")
public class ProjectWorkLogController {
    private final ProjectWorkLogService service;

    public ProjectWorkLogController(ProjectWorkLogService service) { this.service = service; }

    @GetMapping("/tasks")
    public List<StageTaskResponse> listTasks(@PathVariable("projectId") String projectId, HttpServletRequest request) {
        return service.listTasks(requireAccountId(request), projectId);
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public StageTaskResponse createTask(@PathVariable("projectId") String projectId,
                                        @Valid @RequestBody CreateStageTaskRequest input, HttpServletRequest request) {
        return service.createTask(requireAccountId(request), projectId, input.stage(), input.text());
    }

    @PatchMapping("/tasks/{taskId}")
    public StageTaskResponse updateTask(@PathVariable("projectId") String projectId,
                                        @PathVariable("taskId") String taskId, @Valid @RequestBody UpdateStageTaskRequest input,
                                        HttpServletRequest request) {
        return service.updateTask(requireAccountId(request), projectId, taskId, input.done());
    }

    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTask(@PathVariable("projectId") String projectId,
                           @PathVariable("taskId") String taskId, HttpServletRequest request) {
        service.deleteTask(requireAccountId(request), projectId, taskId);
    }

    @GetMapping("/activities")
    public List<ProjectActivityResponse> listActivities(@PathVariable("projectId") String projectId, HttpServletRequest request) {
        return service.listActivities(requireAccountId(request), projectId);
    }

    @PostMapping("/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectActivityResponse createActivity(@PathVariable("projectId") String projectId,
                                                  @Valid @RequestBody CreateProjectActivityRequest input, HttpServletRequest request) {
        return service.createActivity(requireAccountId(request), projectId, input.text());
    }
}
