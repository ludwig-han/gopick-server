package com.gopick.server.project.service;

import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.domain.ProfileTechnology;
import com.gopick.server.profile.repository.ProfileRepository;
import com.gopick.server.profile.repository.ProfileRoleRepository;
import com.gopick.server.profile.repository.ProfileTechnologyRepository;
import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.domain.Recruitment;
import com.gopick.server.project.dto.RecommendationResponse;
import com.gopick.server.project.exception.ProjectNotFoundException;
import com.gopick.server.project.exception.ProjectValidationException;
import com.gopick.server.project.repository.ProjectMembershipRepository;
import com.gopick.server.project.repository.ProjectRepository;
import com.gopick.server.project.repository.RecruitmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class RecommendationService {

    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final RecruitmentRepository recruitments;
    private final ProfileRepository profiles;
    private final ProjectMembershipRepository memberships;
    private final ProfileRoleRepository profileRoles;
    private final ProfileTechnologyRepository profileTechnologies;

    public RecommendationService(
            ProjectAccess access,
            ProjectRepository projects,
            RecruitmentRepository recruitments,
            ProfileRepository profiles,
            ProjectMembershipRepository memberships,
            ProfileRoleRepository profileRoles,
            ProfileTechnologyRepository profileTechnologies
    ) {
        this.access = access;
        this.projects = projects;
        this.recruitments = recruitments;
        this.profiles = profiles;
        this.memberships = memberships;
        this.profileRoles = profileRoles;
        this.profileTechnologies = profileTechnologies;
    }

    public RecommendationResponse recommend(
            Long accountId,
            String projectId,
            String role
    ) {
        // 1. 로그인 확인
        access.requireAccount(accountId);

        // 2. projectId 검증 및 변환
        Long parsedProjectId = access.parseId(projectId);

        // 3. 프로젝트 존재 확인
        projects.findById(parsedProjectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 4. 요청자가 프로젝트 리더인지 확인
        access.requireLeader(parsedProjectId, accountId);

        // 5. BACKEND 같은 역할 문자열을 ProjectRole enum으로 변환
        ProjectRole parsedRole = parseRole(role);

        // 6. 이 프로젝트가 실제로 해당 역할을 모집하는지 확인
        Recruitment recruitment = recruitments
                .findByProject_IdAndRole(parsedProjectId, parsedRole)
                .orElseThrow(() -> new ProjectValidationException(
                        "role",
                        "해당 프로젝트에서 모집하지 않는 역할입니다."
                ));

        // 7. 추천 후보 가져오기
        List<Profile> candidates = findCandidates(parsedProjectId);

        // 8. 후보들의 역할 정보 가져오기
        Map<Long, Set<String>> rolesByProfileId =
                loadRoles(candidates);

        // 9. 후보들의 기술 정보 가져오기
        Map<Long, List<ProfileTechnology>> technologiesByProfileId =
                loadTechnologies(candidates);

        // 10. 기술 조건이 REQUIRED라면
        // 필수 기술을 만족하지 못하는 후보는 제거
        if (recruitment.getTechMode() == ProjectConditionMode.REQUIRED) {
            candidates = candidates.stream()
                    .filter(profile ->
                            satisfiesRequiredTechnologies(
                                    profile,
                                    recruitment,
                                    technologiesByProfileId
                            )
                    )
                    .toList();
        }

        // 11. 최종 후보를 응답 DTO로 변환
        List<RecommendationResponse.Item> items =
                candidates.stream()
                        .map(profile ->
                                createItem(
                                        profile,
                                        parsedRole,
                                        recruitment,
                                        rolesByProfileId,
                                        technologiesByProfileId
                                )
                        )
                        .toList();

        return new RecommendationResponse(
                parsedProjectId,
                parsedRole,
                items
        );
    }

    private List<Profile> findCandidates(Long projectId) {

        // 공개 프로필 전체 조회
        List<Profile> publicProfiles =
                profiles.searchPublicProfiles(null, null, null);

        // 현재 프로젝트에 이미 참가 중인 사람들의 profileId
        Set<Long> memberProfileIds =
                memberships.findMembers(projectId)
                        .stream()
                        .map(member -> member.getProfile().getId())
                        .collect(Collectors.toSet());

        // 이미 팀원인 사람 제외
        return publicProfiles.stream()
                .filter(profile ->
                        !memberProfileIds.contains(profile.getId())
                )
                .toList();
    }

    private Map<Long, Set<String>> loadRoles(
            List<Profile> candidates
    ) {
        List<Long> candidateIds =
                candidates.stream()
                        .map(Profile::getId)
                        .toList();

        if (candidateIds.isEmpty()) {
            return Map.of();
        }

        return profileRoles
                .findAllByProfile_IdInOrderByIdAsc(candidateIds)
                .stream()
                .collect(Collectors.groupingBy(
                        profileRole ->
                                profileRole.getProfile().getId(),

                        Collectors.mapping(
                                profileRole ->
                                        profileRole.getRole()
                                                .trim()
                                                .toUpperCase(Locale.ROOT),
                                Collectors.toSet()
                        )
                ));
    }

    private Map<Long, List<ProfileTechnology>> loadTechnologies(
            List<Profile> candidates
    ) {
        List<Long> candidateIds =
                candidates.stream()
                        .map(Profile::getId)
                        .toList();

        if (candidateIds.isEmpty()) {
            return Map.of();
        }

        return profileTechnologies
                .findAllByProfile_IdInOrderByIdAsc(candidateIds)
                .stream()
                .collect(Collectors.groupingBy(
                        technology ->
                                technology.getProfile().getId()
                ));
    }

    private boolean satisfiesRequiredTechnologies(
            Profile profile,
            Recruitment recruitment,
            Map<Long, List<ProfileTechnology>> technologiesByProfileId
    ) {
        List<ProfileTechnology> profileTechs =
                technologiesByProfileId.getOrDefault(
                        profile.getId(),
                        List.of()
                );

        // 프로젝트가 요구하는 모든 기술을 만족해야 true
        return recruitment.getTechnologies()
                .stream()
                .allMatch(required ->
                        profileTechs.stream()
                                .anyMatch(actual ->
                                        actual.getTechnology()
                                                .equalsIgnoreCase(
                                                        required.technology()
                                                )
                                                &&
                                                actual.getLevel()
                                                        >= required.minLevel()
                                )
                );
    }

    private RecommendationResponse.Item createItem(
            Profile profile,
            ProjectRole role,
            Recruitment recruitment,
            Map<Long, Set<String>> rolesByProfileId,
            Map<Long, List<ProfileTechnology>> technologiesByProfileId
    ) {
        List<RecommendationResponse.Reason> matched =
                new ArrayList<>();

        List<RecommendationResponse.Reason> unmatched =
                new ArrayList<>();


        // ROLE 비교
        boolean roleMatched =
                rolesByProfileId
                        .getOrDefault(
                                profile.getId(),
                                Set.of()
                        )
                        .contains(role.name());

        if (roleMatched) {
            matched.add(
                    new RecommendationResponse.Reason(
                            RecommendationResponse.Category.ROLE,
                            role.name() + " 희망"
                    )
            );
        } else {
            unmatched.add(
                    new RecommendationResponse.Reason(
                            RecommendationResponse.Category.ROLE,
                            role.name() + " 희망 아님"
                    )
            );
        }


        // STACK 비교
        List<ProfileTechnology> profileTechs =
                technologiesByProfileId.getOrDefault(
                        profile.getId(),
                        List.of()
                );

        for (var required : recruitment.getTechnologies()) {

            profileTechs.stream()
                    .filter(actual ->
                            actual.getTechnology()
                                    .equalsIgnoreCase(
                                            required.technology()
                                    )
                                    &&
                                    actual.getLevel()
                                            >= required.minLevel()
                    )
                    .findFirst()
                    .ifPresent(actual ->
                            matched.add(
                                    new RecommendationResponse.Reason(
                                            RecommendationResponse.Category.STACK,
                                            actual.getTechnology()
                                                    + " Lv."
                                                    + actual.getLevel()
                                                    + " 보유"
                                    )
                            )
                    );
        }


        // 아직 점수 계산 전이므로 모두 rank 1
        return new RecommendationResponse.Item(
                profile.getId(),
                profile.getName(),
                1,
                matched,
                unmatched,
                null
        );
    }

    private ProjectRole parseRole(String role) {
        try {
            return ProjectRole.valueOf(
                    role.toUpperCase(Locale.ROOT)
            );
        } catch (
                IllegalArgumentException |
                NullPointerException e
        ) {
            throw new ProjectValidationException(
                    "role",
                    "허용되지 않는 역할입니다."
            );
        }
    }
}