package com.gopick.server.project.service;

import com.gopick.server.profile.domain.ExperienceAttribute;
import com.gopick.server.profile.domain.ExperienceAttributeCategory;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.domain.ProfileExperience;

import com.gopick.server.profile.repository.ProfileExperienceAttributeRepository;
import com.gopick.server.profile.repository.ProfileExperienceRepository;
import com.gopick.server.profile.repository.ProfileInterestRepository;
import com.gopick.server.profile.repository.ProfilePartRepository;
import com.gopick.server.profile.repository.ProfilePreferenceRepository;
import com.gopick.server.profile.repository.ProfileRoleRepository;
import com.gopick.server.profile.repository.ProfileTechnologyRepository;

import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.domain.Recruitment;
import com.gopick.server.project.domain.RecruitmentTechnology;

import com.gopick.server.project.dto.RecommendationAiRequest;
import com.gopick.server.project.dto.RecommendationAiResult;
import com.gopick.server.project.dto.RecommendationResponse;

import com.gopick.server.project.exception.ProjectAccessDeniedException;
import com.gopick.server.project.exception.ProjectNotFoundException;
import com.gopick.server.project.exception.ProjectValidationException;

import com.gopick.server.project.repository.ProjectRepository;
import com.gopick.server.project.repository.RecommendationCandidateRepository;
import com.gopick.server.project.repository.RecommendationCandidateRepository.ExperienceEvidence;
import com.gopick.server.project.repository.RecommendationCandidateRepository.TechnologyEvidence;
import com.gopick.server.project.repository.RecruitmentRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private static final int MAX_CANDIDATES = 20;
    private static final int ELIGIBILITY_PAGE_SIZE = 100;

    private final ProjectAccess access;

    private final ProjectRepository projects;
    private final RecruitmentRepository recruitments;
    private final RecommendationCandidateRepository candidateRepository;

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
            RecommendationCandidateRepository candidateRepository,
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
        this.candidateRepository = candidateRepository;
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
        RecommendationAiRequest aiRequest = RecommendationExperiencePolicy.sanitizeRequest(prepared.request());
        RecommendationAiResult aiResult = RecommendationExperiencePolicy.sanitizeResult(
                aiRequest, aiClient.recommend(aiRequest));

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

        if (!candidateRepository.hasLeader(projectId, accountId)) {
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

        List<Long> candidateIds = findEligibleCandidateIds(project, recruitment);
        List<Profile> candidates = candidateIds.isEmpty()
                ? List.of() : candidateRepository.findByIdInOrderByIdDesc(candidateIds);
        CandidateData data = loadCandidateData(candidates);

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

                        project.getExperienceMode() == null ? ProjectConditionMode.NONE : project.getExperienceMode(),

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

    private List<Long> findEligibleCandidateIds(Project project, Recruitment recruitment) {
        var requiredTechnologies = recruitment.getTechMode() == ProjectConditionMode.REQUIRED
                ? recruitment.getTechnologies() : List.<RecruitmentTechnology>of();
        boolean experienceRequired = project.getExperienceMode() == ProjectConditionMode.REQUIRED;
        if (requiredTechnologies.isEmpty() && !experienceRequired) {
            return candidateRepository.findPublicNonMemberIds(project.getId(), null, PageRequest.of(0, MAX_CANDIDATES));
        }

        Set<String> requiredTypes = project.getExperienceTypes().stream()
                .map(this::normalize).collect(Collectors.toSet());
        Set<String> requiredFields = project.getExperienceFields().stream()
                .map(this::normalize).collect(Collectors.toSet());
        var eligible = new ArrayList<Long>(MAX_CANDIDATES);
        var pageRequest = PageRequest.of(0, ELIGIBILITY_PAGE_SIZE);
        Long beforeId = null;
        while (eligible.size() < MAX_CANDIDATES) {
            var page = candidateRepository.findPublicNonMemberIds(project.getId(), beforeId, pageRequest);
            if (page.isEmpty()) {
                break;
            }

            // Apply the existing Java normalization before choosing the newest eligible profiles.
            var technologyMatches = filterRequiredTechnologies(page, requiredTechnologies);
            var experienceMatches = experienceRequired
                    ? filterRequiredExperience(technologyMatches, requiredTypes, requiredFields) : technologyMatches;
            eligible.addAll(experienceMatches.stream().limit(MAX_CANDIDATES - eligible.size()).toList());

            if (page.size() < ELIGIBILITY_PAGE_SIZE) {
                break;
            }
            beforeId = page.getLast();
        }
        return eligible;
    }

    private List<Long> filterRequiredTechnologies(List<Long> candidateIds, List<RecruitmentTechnology> required) {
        if (required.isEmpty()) {
            return candidateIds;
        }

        var technologiesByProfile = candidateRepository.findTechnologyEvidence(candidateIds).stream()
                .collect(Collectors.groupingBy(TechnologyEvidence::getProfileId,
                        Collectors.toMap(t -> normalize(t.getName()), TechnologyEvidence::getLevel, Math::max)));
        return candidateIds.stream().filter(id -> {
            var levels = technologiesByProfile.getOrDefault(id, Map.of());
            return required.stream().allMatch(t -> levels.getOrDefault(normalize(t.technology()), 0) >= t.minLevel());
        }).toList();
    }

    private List<Long> filterRequiredExperience(List<Long> candidateIds, Set<String> requiredTypes,
                                                Set<String> requiredFields) {
        if (candidateIds.isEmpty()) {
            return candidateIds;
        }

        var experiencesByProfile = candidateRepository.findExperienceEvidence(candidateIds).stream()
                .collect(Collectors.groupingBy(ExperienceEvidence::getProfileId,
                        Collectors.groupingBy(ExperienceEvidence::getExperienceId)));
        return candidateIds.stream().filter(id -> experiencesByProfile.getOrDefault(id, Map.of()).values().stream()
                .anyMatch(evidence -> matchesAttributes(evidence, ExperienceAttributeCategory.TYPE, requiredTypes)
                        && matchesAttributes(evidence, ExperienceAttributeCategory.FIELD, requiredFields))).toList();
    }

    private boolean matchesAttributes(List<ExperienceEvidence> evidence, ExperienceAttributeCategory category,
                                      Set<String> requiredValues) {
        return requiredValues.isEmpty() || evidence.stream()
                .anyMatch(item -> item.getCategory() == category
                        && requiredValues.contains(normalize(item.getAttributeValue())));
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
