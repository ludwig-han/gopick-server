package com.gopick.server.project.service;

import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.exception.ProfileNotFoundException;
import com.gopick.server.profile.repository.ProfileRepository;
import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectMember;
import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.dto.ProjectCreateRequest;
import com.gopick.server.project.dto.ProjectCreateResponse;
import com.gopick.server.project.repository.ProjectMemberRepository;
import com.gopick.server.project.repository.ProjectRepository;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProfileRepository profileRepository;
    private final ProjectRecruitmentEditor recruitmentEditor;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository projectMemberRepository,
                          ProfileRepository profileRepository, ProjectRecruitmentEditor recruitmentEditor) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.profileRepository = profileRepository;
        this.recruitmentEditor = recruitmentEditor;
    }

    @Transactional
    public ProjectCreateResponse createProject(Long accountId, ProjectCreateRequest projectCreateRequest) {
        // profile check
        Profile profile = profileRepository.findByAccount_Id(accountId)
                .orElseThrow(ProfileNotFoundException::new);

        // 2. new Project 객체 생성
        Project project = new Project(
                projectCreateRequest.getExtra(),
                projectCreateRequest.getWish(),
                projectCreateRequest.getGoal(),
                projectCreateRequest.getBackground(),
                projectCreateRequest.getDescription(),
                projectCreateRequest.getTitle(),
                projectCreateRequest.getRecruiting());
        recruitmentEditor.apply(project, projectCreateRequest);

        // 3. db에 저장 => saveProject
        Project savedProject = projectRepository.save(project);

        // 4. savedProject.getId()로 ProjectMember 등록, leader로, save
        ProjectMember projectMember = new ProjectMember(
                savedProject,
                profile,
                ProjectMemberType.LEADER,
                projectCreateRequest.getRole()
        );
        projectMemberRepository.save(projectMember);

        // 5. new ProjectCreateResponse 생성 반환
        return new ProjectCreateResponse(project.getId());
    }
}
