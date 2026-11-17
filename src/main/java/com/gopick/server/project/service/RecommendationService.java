package com.gopick.server.project.service;

import com.gopick.server.profile.domain.ExperienceAttribute;
import com.gopick.server.profile.domain.ExperienceAttributeCategory;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.domain.ProfileExperience;
import com.gopick.server.profile.domain.ProfileTechnology;

import com.gopick.server.profile.repository.ProfileExperienceAttributeRepository;
import com.gopick.server.profile.repository.ProfileExperienceRepository;
import com.gopick.server.profile.repository.ProfileInterestRepository;
import com.gopick.server.profile.repository.ProfilePartRepository;
import com.gopick.server.profile.repository.ProfilePreferenceRepository;
import com.gopick.server.profile.repository.ProfileRepository;
import com.gopick.server.profile.repository.ProfileRoleRepository;
import com.gopick.server.profile.repository.ProfileTechnologyRepository;

import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectMember;
import com.gopick.server.project.domain.ProjectMemberType;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.domain.Recruitment;

import com.gopick.server.project.dto.RecommendationAiRequest;
import com.gopick.server.project.dto.RecommendationAiResult;
import com.gopick.server.project.dto.RecommendationResponse;

import com.gopick.server.project.exception.ProjectAccessDeniedException;
import com.gopick.server.project.exception.ProjectNotFoundException;
import com.gopick.server.project.exception.ProjectValidationException;

import com.gopick.server.project.repository.ProjectMembershipRepository;
import com.gopick.server.project.repository.ProjectRepository;
import com.gopick.server.project.repository.RecruitmentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final ProjectAccess access;

    private final ProjectRepository projects;
    private final RecruitmentRepository recruitments;
    private final ProjectMembershipRepository memberships;

    private final ProfileRepository profiles;
    private final ProfileTechnologyRepository profileTechnologies;
    private final ProfileRoleRepository profileRoles;
    private final ProfileInterestRepository profileInterests;
    private final ProfilePreferenceRepository profilePreferences;
    private final ProfilePartRepository profileParts;
    private final ProfileExperienceRepository profileExperiences;
    private final ProfileExperienceAttributeRepository experienceAttributes;

    private final RecommendationAiClient aiClient;

    private final TransactionTemplate readTransaction;

    public RecommendationService(
            ProjectAccess access,
            ProjectRepository projects,
            RecruitmentRepository recruitments,
            ProjectMembershipRepository memberships,
            ProfileRepository profiles,
            ProfileTechnologyRepository profileTechnologies,
            ProfileRoleRepository profileRoles,
            ProfileInterestRepository profileInterests,
            ProfilePreferenceRepository profilePreferences,
            ProfilePartRepository profileParts,
            ProfileExperienceRepository profileExperiences,
            ProfileExperienceAttributeRepository experienceAttributes,
            RecommendationAiClient aiClient,
            PlatformTransactionManager transactionManager
    ) {
        this.access = access;
        this.projects = projects;
        this.recruitments = recruitments;
        this.memberships = memberships;
        this.profiles = profiles;
        this.profileTechnologies = profileTechnologies;
        this.profileRoles = profileRoles;
        this.profileInterests = profileInterests;
        this.profilePreferences = profilePreferences;
        this.profileParts = profileParts;
        this.profileExperiences = profileExperiences;
        this.experienceAttributes = experienceAttributes;
        this.aiClient = aiClient;

        this.readTransaction =
                new TransactionTemplate(transactionManager);

        this.readTransaction.setReadOnly(true);
    }

    public RecommendationResponse recommend(
            Long accountId,
            String projectId,
            String role
    ) {
        /*
         * DB 읽기/필터링은 transaction 안에서 끝낸다.
         *
         * AI 호출은 그 다음에 실행해서
         * AI 기다리는 동안 DB transaction을 잡지 않는다.
         */
        PreparedRecommendation prepared =
                readTransaction.execute(status ->
                        prepare(
                                accountId,
                                projectId,
                                role
                        )
                );

        if (prepared == null) {
            throw new IllegalStateException(
                    "추천 요청을 준비하지 못했습니다."
            );
        }

        if (prepared.request()
                .candidates()
                .isEmpty()) {

            return new RecommendationResponse(
                    prepared.projectId(),
                    prepared.role(),
                    List.of()
            );
        }

        /*
         * 실제 AI 호출
         */
        RecommendationAiResult aiResult =
                aiClient.recommend(
                        prepared.request()
                );

        /*
         * AI 결과를 그대로 믿지 않고 검증한 뒤
         * 프론트 응답으로 바꾼다.
         */
        List<RecommendationResponse.Item> items =
                convertAiResult(
                        prepared.request(),
                        aiResult
                );

        return new RecommendationResponse(
                prepared.projectId(),
                prepared.role(),
                items
        );
    }

    private PreparedRecommendation prepare(
            Long accountId,
            String rawProjectId,
            String rawRole
    ) {
        access.requireAccount(accountId);

        Long projectId =
                access.parseId(rawProjectId);

        ProjectRole role =
                parseRole(rawRole);

        Project project =
                projects.findById(projectId)
                        .orElseThrow(
                                ProjectNotFoundException::new
                        );

        /*
         * 추천 API는 읽기 기능이므로
         * requireLeader()의 FOR UPDATE를 사용하지 않는다.
         */
        List<ProjectMember> projectMembers =
                memberships.findMembers(projectId);

        boolean isLeader =
                projectMembers.stream()
                        .anyMatch(member ->
                                member.getMemberType()
                                        == ProjectMemberType.LEADER
                                        &&
                                        member.getProfile()
                                                .getAccount()
                                                .getId()
                                                .equals(accountId)
                        );

        if (!isLeader) {
            throw new ProjectAccessDeniedException();
        }

        Recruitment recruitment =
                recruitments
                        .findByProject_IdAndRole(
                                projectId,
                                role
                        )
                        .orElseThrow(() ->
                                new ProjectValidationException(
                                        "role",
                                        "해당 프로젝트에서 모집하지 않는 역할입니다."
                                )
                        );

        /*
         * 공개 프로필
         */
        List<Profile> candidates =
                profiles.searchPublicProfiles(
                        null,
                        null,
                        null
                );

        /*
         * 이미 팀원인 사람 제외
         */
        Set<Long> memberProfileIds =
                projectMembers.stream()
                        .map(member ->
                                member.getProfile().getId()
                        )
                        .collect(Collectors.toSet());

        candidates =
                candidates.stream()
                        .filter(profile ->
                                !memberProfileIds.contains(
                                        profile.getId()
                                )
                        )
                        .toList();

        /*
         * 후보 데이터 일괄 조회
         */
        CandidateData data =
                loadCandidateData(candidates);

        /*
         * REQUIRED 기술은 백엔드가 강제한다.
         */
        if (recruitment.getTechMode()
                == ProjectConditionMode.REQUIRED) {

            candidates =
                    candidates.stream()
                            .filter(profile ->
                                    satisfiesRequiredTechnologies(
                                            profile,
                                            recruitment,
                                            data.technologies()
                                    )
                            )
                            .toList();
        }

        /*
         * REQUIRED 경험도 백엔드가 강제한다.
         */
        if (project.getExperienceMode()
                == ProjectConditionMode.REQUIRED) {

            candidates =
                    candidates.stream()
                            .filter(profile ->
                                    matchesExperience(
                                            profile,
                                            project,
                                            data.experiences(),
                                            data.experienceAttributes()
                                    )
                            )
                            .toList();
        }

        RecommendationAiRequest request =
                buildAiRequest(
                        project,
                        role,
                        recruitment,
                        candidates,
                        data
                );

        return new PreparedRecommendation(
                projectId,
                role,
                request
        );
    }

    private CandidateData loadCandidateData(
            List<Profile> candidates
    ) {
        List<Long> ids =
                candidates.stream()
                        .map(Profile::getId)
                        .toList();

        if (ids.isEmpty()) {
            return CandidateData.empty();
        }

        var technologies =
                profileTechnologies
                        .findAllByProfile_IdInOrderByIdAsc(ids)
                        .stream()
                        .collect(Collectors.groupingBy(
                                technology ->
                                        technology.getProfile()
                                                .getId()
                        ));

        var roles =
                profileRoles
                        .findAllByProfile_IdInOrderByIdAsc(ids)
                        .stream()
                        .collect(Collectors.groupingBy(
                                role ->
                                        role.getProfile()
                                                .getId()
                        ));

        var interests =
                profileInterests
                        .findAllByProfile_IdInOrderByIdAsc(ids)
                        .stream()
                        .collect(Collectors.groupingBy(
                                interest ->
                                        interest.getProfile()
                                                .getId()
                        ));

        var preferences =
                profilePreferences
                        .findAllByProfile_IdInOrderByIdAsc(ids)
                        .stream()
                        .collect(Collectors.groupingBy(
                                preference ->
                                        preference.getProfile()
                                                .getId()
                        ));

        var parts =
                profileParts
                        .findAllByProfile_IdInOrderByIdAsc(ids)
                        .stream()
                        .collect(Collectors.groupingBy(
                                part ->
                                        part.getProfile()
                                                .getId()
                        ));

        var experiences =
                profileExperiences
                        .findAllByProfile_IdInOrderByIdAsc(ids)
                        .stream()
                        .collect(Collectors.groupingBy(
                                experience ->
                                        experience.getProfile()
                                                .getId()
                        ));

        List<Long> experienceIds =
                experiences.values()
                        .stream()
                        .flatMap(List::stream)
                        .map(ProfileExperience::getId)
                        .toList();

        Map<Long, List<ExperienceAttribute>>
                attributesByExperience;

        if (experienceIds.isEmpty()) {
            attributesByExperience =
                    Map.of();
        } else {
            attributesByExperience =
                    experienceAttributes
                            .findAllByExperience_IdInOrderByIdAsc(
                                    experienceIds
                            )
                            .stream()
                            .collect(Collectors.groupingBy(
                                    attribute ->
                                            attribute
                                                    .getExperience()
                                                    .getId()
                            ));
        }

        return new CandidateData(
                technologies,
                roles,
                interests,
                preferences,
                parts,
                experiences,
                attributesByExperience
        );
    }

    private RecommendationAiRequest buildAiRequest(
            Project project,
            ProjectRole role,
            Recruitment recruitment,
            List<Profile> candidates,
            CandidateData data
    ) {
        var projectData =
                new RecommendationAiRequest.ProjectData(
                        project.getId(),
                        role,
                        project.getTitle(),
                        project.getDescription(),
                        project.getBackground(),
                        project.getGoal(),
                        project.getWish(),

                        recruitment.getTechMode(),

                        recruitment.getTechnologies()
                                .stream()
                                .map(technology ->
                                        new RecommendationAiRequest
                                                .TechnologyCondition(
                                                technology.technology(),
                                                technology.minLevel()
                                        )
                                )
                                .toList(),

                        project.getExperienceMode(),

                        List.copyOf(
                                project.getExperienceTypes()
                        ),

                        List.copyOf(
                                project.getExperienceFields()
                        ),

                        project.getAttributes()
                                .stream()
                                .map(attribute ->
                                        new RecommendationAiRequest.Attribute(
                                                attribute.category(),
                                                attribute.value()
                                        )
                                )
                                .toList()
                );

        List<RecommendationAiRequest.Candidate>
                candidateData =
                candidates.stream()
                        .map(profile ->
                                toAiCandidate(
                                        profile,
                                        data
                                )
                        )
                        .toList();

        return new RecommendationAiRequest(
                projectData,
                candidateData
        );
    }

    private RecommendationAiRequest.Candidate toAiCandidate(
            Profile profile,
            CandidateData data
    ) {
        Long profileId =
                profile.getId();

        var technologies =
                data.technologies()
                        .getOrDefault(
                                profileId,
                                List.of()
                        )
                        .stream()
                        .map(technology ->
                                new RecommendationAiRequest.Technology(
                                        technology.getTechnology(),
                                        technology.getLevel()
                                )
                        )
                        .toList();

        var roles =
                data.roles()
                        .getOrDefault(
                                profileId,
                                List.of()
                        )
                        .stream()
                        .map(role ->
                                role.getRole()
                        )
                        .toList();

        var interests =
                data.interests()
                        .getOrDefault(
                                profileId,
                                List.of()
                        )
                        .stream()
                        .map(interest ->
                                interest.getField()
                        )
                        .toList();

        var preferences =
                data.preferences()
                        .getOrDefault(
                                profileId,
                                List.of()
                        )
                        .stream()
                        .map(preference ->
                                new RecommendationAiRequest.Preference(
                                        preference.getCategory(),
                                        preference.getValue()
                                )
                        )
                        .toList();

        var parts =
                data.parts()
                        .getOrDefault(
                                profileId,
                                List.of()
                        )
                        .stream()
                        .map(part ->
                                part.getPart()
                        )
                        .toList();

        var experiences =
                data.experiences()
                        .getOrDefault(
                                profileId,
                                List.of()
                        )
                        .stream()
                        .map(experience -> {

                            var attributes =
                                    data.experienceAttributes()
                                            .getOrDefault(
                                                    experience.getId(),
                                                    List.of()
                                            )
                                            .stream()
                                            .map(attribute ->
                                                    new RecommendationAiRequest
                                                            .ExperienceAttribute(
                                                            attribute
                                                                    .getCategory(),
                                                            attribute
                                                                    .getValue()
                                                    )
                                            )
                                            .toList();

                            return new RecommendationAiRequest.Experience(
                                    experience.getDescription(),
                                    attributes
                            );
                        })
                        .toList();

        return new RecommendationAiRequest.Candidate(
                profileId,
                profile.getName(),
                profile.getMajor(),
                profile.getTeamTendency(),
                profile.getIntro(),
                parts,
                roles,
                technologies,
                interests,
                preferences,
                experiences
        );
    }

    private boolean satisfiesRequiredTechnologies(
            Profile profile,
            Recruitment recruitment,
            Map<Long, List<ProfileTechnology>>
                    technologiesByProfile
    ) {
        List<ProfileTechnology> actual =
                technologiesByProfile
                        .getOrDefault(
                                profile.getId(),
                                List.of()
                        );

        return recruitment.getTechnologies()
                .stream()
                .allMatch(required ->
                        actual.stream()
                                .anyMatch(technology ->
                                        normalize(
                                                technology
                                                        .getTechnology()
                                        )
                                                .equals(
                                                        normalize(
                                                                required
                                                                        .technology()
                                                        )
                                                )
                                                &&
                                                technology.getLevel()
                                                        >= required.minLevel()
                                )
                );
    }

    private boolean matchesExperience(
            Profile profile,
            Project project,
            Map<Long, List<ProfileExperience>>
                    experiencesByProfile,
            Map<Long, List<ExperienceAttribute>>
                    attributesByExperience
    ) {
        List<ProfileExperience> experiences =
                experiencesByProfile
                        .getOrDefault(
                                profile.getId(),
                                List.of()
                        );

        if (experiences.isEmpty()) {
            return false;
        }

        Set<String> requiredTypes =
                project.getExperienceTypes()
                        .stream()
                        .map(this::normalize)
                        .collect(Collectors.toSet());

        Set<String> requiredFields =
                project.getExperienceFields()
                        .stream()
                        .map(this::normalize)
                        .collect(Collectors.toSet());

        /*
         * 경험 세부조건이 없으면
         * 경험 하나라도 있으면 충족
         */
        if (requiredTypes.isEmpty()
                && requiredFields.isEmpty()) {

            return true;
        }

        return experiences.stream()
                .anyMatch(experience -> {

                    var attributes =
                            attributesByExperience
                                    .getOrDefault(
                                            experience.getId(),
                                            List.of()
                                    );

                    Set<String> types =
                            attributes.stream()
                                    .filter(attribute ->
                                            attribute.getCategory()
                                                    == ExperienceAttributeCategory.TYPE
                                    )
                                    .map(attribute ->
                                            normalize(
                                                    attribute.getValue()
                                            )
                                    )
                                    .collect(Collectors.toSet());

                    Set<String> fields =
                            attributes.stream()
                                    .filter(attribute ->
                                            attribute.getCategory()
                                                    == ExperienceAttributeCategory.FIELD
                                    )
                                    .map(attribute ->
                                            normalize(
                                                    attribute.getValue()
                                            )
                                    )
                                    .collect(Collectors.toSet());

                    boolean typeMatched =
                            requiredTypes.isEmpty()
                                    ||
                                    types.stream()
                                            .anyMatch(
                                                    requiredTypes::contains
                                            );

                    boolean fieldMatched =
                            requiredFields.isEmpty()
                                    ||
                                    fields.stream()
                                            .anyMatch(
                                                    requiredFields::contains
                                            );

                    return typeMatched
                            && fieldMatched;
                });
    }

    /*
     * AI 결과 검증 + dense rank
     */
    private List<RecommendationResponse.Item>
    convertAiResult(
            RecommendationAiRequest request,
            RecommendationAiResult result
    ) {
        if (result == null
                || result.items() == null) {
            return List.of();
        }

        Map<Long, RecommendationAiRequest.Candidate>
                candidatesById =
                request.candidates()
                        .stream()
                        .collect(Collectors.toMap(
                                RecommendationAiRequest
                                        .Candidate::profileId,
                                Function.identity()
                        ));

        /*
         * AI가 같은 profileId를 두 번 보내더라도
         * 첫 번째 것만 사용
         */
        Map<Long, RecommendationAiResult.Item>
                uniqueItems =
                new LinkedHashMap<>();

        for (var item : result.items()) {

            if (item == null
                    || item.profileId() == null) {
                continue;
            }

            /*
             * AI가 존재하지 않는 후보를 만들어냈다면 제거
             */
            if (!candidatesById.containsKey(
                    item.profileId()
            )) {
                continue;
            }

            uniqueItems.putIfAbsent(
                    item.profileId(),
                    item
            );
        }

        /*
         * score 높은 순.
         *
         * Java sorted는 stable이므로
         * 같은 score면 AI가 준 순서를 유지.
         */
        List<RecommendationAiResult.Item> sorted =
                uniqueItems.values()
                        .stream()
                        .sorted(
                                Comparator.comparingInt(
                                        RecommendationAiResult
                                                .Item::score
                                ).reversed()
                        )
                        .toList();

        List<RecommendationResponse.Item> response =
                new ArrayList<>();

        Integer previousScore = null;
        int rank = 0;

        for (var item : sorted) {

            if (previousScore == null
                    || item.score()
                    != previousScore) {

                rank++;

                previousScore =
                        item.score();
            }

            var candidate =
                    candidatesById.get(
                            item.profileId()
                    );

            response.add(
                    new RecommendationResponse.Item(
                            item.profileId(),
                            candidate.name(),
                            rank,

                            item.matched() == null
                                    ? List.of()
                                    : item.matched(),

                            item.unmatched() == null
                                    ? List.of()
                                    : item.unmatched(),

                            item.summary()
                    )
            );
        }

        return response;
    }

    private ProjectRole parseRole(
            String role
    ) {
        try {
            return ProjectRole.valueOf(
                    role.trim()
                            .toUpperCase(
                                    Locale.ROOT
                            )
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

    private String normalize(
            String value
    ) {
        return value == null
                ? ""
                : value.trim()
                  .toLowerCase(
                          Locale.ROOT
                  );
    }

    private record PreparedRecommendation(
            Long projectId,
            ProjectRole role,
            RecommendationAiRequest request
    ) {}

    private record CandidateData(
            Map<Long, List<com.gopick.server.profile.domain.ProfileTechnology>> technologies,
            Map<Long, List<com.gopick.server.profile.domain.ProfileRole>> roles,
            Map<Long, List<com.gopick.server.profile.domain.ProfileInterest>> interests,
            Map<Long, List<com.gopick.server.profile.domain.ProfilePreference>> preferences,
            Map<Long, List<com.gopick.server.profile.domain.ProfilePart>> parts,
            Map<Long, List<ProfileExperience>> experiences,
            Map<Long, List<ExperienceAttribute>> experienceAttributes
    ) {
        static CandidateData empty() {
            return new CandidateData(
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of()
            );
        }
    }
}