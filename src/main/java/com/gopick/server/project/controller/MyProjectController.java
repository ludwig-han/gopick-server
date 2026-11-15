package com.gopick.server.project.controller;

import com.gopick.server.project.dto.ProjectListResponse;
import com.gopick.server.project.service.ProjectReadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
public class MyProjectController {
    private final ProjectReadService service;
    public MyProjectController(ProjectReadService service) { this.service = service; }

    @GetMapping("/me/projects")
    public List<ProjectListResponse> list(HttpServletRequest request) {
        return service.myProjects(requireAccountId(request));
    }
}
