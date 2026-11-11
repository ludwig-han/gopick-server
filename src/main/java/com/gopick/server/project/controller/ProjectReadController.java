package com.gopick.server.project.controller;

import com.gopick.server.project.dto.ProjectReadResponse;
import com.gopick.server.project.dto.ProjectResponse;
import com.gopick.server.project.service.ProjectReadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
@RequestMapping("/projects")
public class ProjectReadController {
    private final ProjectReadService service;
    public ProjectReadController(ProjectReadService service) { this.service = service; }
    @GetMapping
    public List<ProjectResponse> list(@RequestParam(value = "status", required = false) String status,
                                      @RequestParam(value = "recruiting", required = false) String recruiting,
                                      HttpServletRequest request) {
        return service.list(requireAccountId(request), status, recruiting);
    }
    @GetMapping("/{projectId}")
    public ProjectReadResponse detail(@PathVariable("projectId") String projectId, HttpServletRequest request) {
        return service.detail(requireAccountId(request), projectId);
    }
}
