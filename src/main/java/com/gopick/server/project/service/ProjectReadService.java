package com.gopick.server.project.service;

import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectStatus;
import com.gopick.server.project.domain.ProjectMember;
import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.dto.ProjectListResponse;
import com.gopick.server.project.dto.ProjectReadResponse;
import com.gopick.server.project.dto.ProjectRecruitmentDetails;
import com.gopick.server.project.exception.ProjectNotFoundException;
import com.gopick.server.project.exception.ProjectValidationException;
import com.gopick.server.project.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProjectReadService {
    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final ProjectSearchRepository search;
    private final ProjectMembershipRepository memberships;
    private final RecruitmentRepository recruitments;
    public ProjectReadService(ProjectAccess access, ProjectRepository projects,
                              ProjectSearchRepository search, ProjectMembershipRepository memberships,
                              RecruitmentRepository recruitments) {
        this.access = access; this.projects = projects; this.search = search; this.memberships = memberships;
        this.recruitments = recruitments;
    }
    public List<ProjectListResponse> list(Long accountId, String status, String recruiting) {
        access.requireAccount(accountId);
        ProjectStatus parsedStatus = null;
        if (status != null) {
            try { parsedStatus = ProjectStatus.valueOf(status); }
            catch (IllegalArgumentException e) { throw new ProjectValidationException("status", "허용되지 않는 상태입니다."); }
        }
        Boolean parsedRecruiting = null;
        if (recruiting != null) {
            if (recruiting.equals("true")) parsedRecruiting = true;
            else if (recruiting.equals("false")) parsedRecruiting = false;
            else throw new ProjectValidationException("recruiting", "true 또는 false여야 합니다.");
        }
        return listDetails(search.search(parsedStatus, parsedRecruiting), accountId, Map.of());
    }

    public List<ProjectListResponse> myProjects(Long accountId) {
        access.requireAccount(accountId);
        var myMemberships = memberships.findProjectsForAccount(accountId);
        var authorities = myMemberships.stream().collect(Collectors.toMap(
                m -> m.getProject().getId(), ProjectMember::getMemberType));
        return listDetails(myMemberships.stream().map(ProjectMember::getProject).toList(), accountId, authorities);
    }

    private List<ProjectListResponse> listDetails(List<Project> projects, Long accountId,
                                                   Map<Long, ProjectMemberType> authorities) {
        if (projects.isEmpty()) return List.of();
        var ids = projects.stream().map(Project::getId).toList();
        search.loadAttributes(ids);
        var membersByProject = memberships.findMembersForProjects(ids).stream()
                .collect(Collectors.groupingBy(m -> m.getProject().getId(),
                        Collectors.mapping(m -> memberResponse(m, accountId), Collectors.toList())));
        var recruitmentByProject = recruitments.findForProjects(ids).stream()
                .collect(Collectors.groupingBy(r -> r.getProject().getId(),
                        Collectors.mapping(r -> new ProjectRecruitmentDetails.RoleRecruitment(
                                r.getRole(), r.getRemainingCount(), r.getTechMode(),
                                r.getTechnologies().stream().map(t -> new ProjectRecruitmentDetails.Technology(
                                        t.technology(), t.minLevel())).toList()), Collectors.toList())));
        return projects.stream().map(p -> ProjectListResponse.from(p,
                membersByProject.getOrDefault(p.getId(), List.of()),
                recruitmentByProject.getOrDefault(p.getId(), List.of()), authorities.get(p.getId()))).toList();
    }

    private ProjectReadResponse.Member memberResponse(ProjectMember member, Long accountId) {
        var profile = member.getProfile();
        if (profile == null) {
            return new ProjectReadResponse.Member(null, "탈퇴한 회원입니다", false,
                    member.getRole(), member.getMemberType(), member.getJoinedAt());
        }
        boolean visible = profile.isPublic() || Objects.equals(profile.getAccount().getId(), accountId);
        return new ProjectReadResponse.Member(profile.getId(), visible ? profile.getName() : null,
                profile.isPublic(), member.getRole(), member.getMemberType(), member.getJoinedAt());
    }
    public ProjectReadResponse detail(Long accountId, String projectId) {
        access.requireAccount(accountId);
        Project p = projects.findById(access.parseId(projectId)).orElseThrow(ProjectNotFoundException::new);
        var members = memberships.findMembers(p.getId()).stream().map(m -> memberResponse(m, accountId)).toList();
        var recruitment = ProjectRecruitmentDetails.from(p, recruitments.findAllByProject_IdOrderByRoleAsc(p.getId()));
        return new ProjectReadResponse(p.getId(), p.getTitle(), p.getDescription(), p.getBackground(), p.getGoal(),
                p.getWish(), p.getExtra(), p.getStatus(), p.getStage(), p.getRecruiting(), p.getCreatedAt(),
                p.getStatusUpdatedAt(), p.getStageUpdatedAt(), members, recruitment.attributes(),
                recruitment.experience(), recruitment.recruitments(), recruitment.links());
    }
}
