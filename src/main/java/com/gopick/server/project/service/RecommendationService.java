package com.gopick.server.project.service;

import com.gopick.server.profile.domain.ExperienceAttribute;
import com.gopick.server.profile.domain.ExperienceAttributeCategory;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.domain.ProfileExperience;
import com.gopick.server.profile.domain.ProfilePreferenceCategory;
import com.gopick.server.profile.domain.ProfileTechnology;
import com.gopick.server.profile.repository.ProfileExperienceAttributeRepository;
import com.gopick.server.profile.repository.ProfileExperienceRepository;
import com.gopick.server.profile.repository.ProfileInterestRepository;
import com.gopick.server.profile.repository.ProfilePreferenceRepository;
import com.gopick.server.profile.repository.ProfileRepository;
import com.gopick.server.profile.repository.ProfileRoleRepository;
import com.gopick.server.profile.repository.ProfileTechnologyRepository;
import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectAttributeCategory;
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
import java.util.Comparator;
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
    private final ProfilePreferenceRepository profilePreferences;
    private final ProfileInterestRepository profileInterests;

    private final ProfileExperienceRepository profileExperiences;
    private final ProfileExperienceAttributeRepository experienceAttributes;

    public RecommendationService(
            ProjectAccess access,
            ProjectRepository projects,
            RecruitmentRepository recruitments,
            ProfileRepository profiles,
            ProjectMembershipRepository memberships,
            ProfileRoleRepository profileRoles,
            ProfileTechnologyRepository profileTechnologies,
            ProfilePreferenceRepository profilePreferences,
            ProfileInterestRepository profileInterests,
            ProfileExperienceRepository profileExperiences,
            ProfileExperienceAttributeRepository experienceAttributes
    ) {
        this.access = access;
        this.projects = projects;
        this.recruitments = recruitments;
        this.profiles = profiles;
        this.memberships = memberships;
        this.profileRoles = profileRoles;
        this.profileTechnologies = profileTechnologies;
        this.profilePreferences = profilePreferences;
        this.profileInterests = profileInterests;
        this.profileExperiences = profileExperiences;
        this.experienceAttributes = experienceAttributes;
    }

    public RecommendationResponse recommend(
            Long accountId,
            String projectId,
            String role
    ) {
        // 로그인 확인
        access.requireAccount(accountId);

        Long parsedProjectId = access.parseId(projectId);

        // 프로젝트 확인
        Project project = projects.findById(parsedProjectId)
                .orElseThrow(ProjectNotFoundException::new);

        // 팀장만 추천 요청 가능
        access.requireLeader(parsedProjectId, accountId);

        ProjectRole parsedRole = parseRole(role);

        // 해당 역할을 실제 모집 중인지 확인
        Recruitment recruitment = recruitments
                .findByProject_IdAndRole(parsedProjectId, parsedRole)
                .orElseThrow(() -> new ProjectValidationException(
                        "role",
                        "해당 프로젝트에서 모집하지 않는 역할입니다."
                ));

        /*
         * 1. 기본 후보
         * - 공개 프로필
         * - 이미 팀원인 사람 제외
         */
        List<Profile> candidates =
                findCandidates(parsedProjectId);

        /*
         * 후보 데이터를 한꺼번에 읽어둔다.
         */
        Map<Long, Set<String>> rolesByProfileId =
                loadRoles(candidates);

        Map<Long, List<ProfileTechnology>> technologiesByProfileId =
                loadTechnologies(candidates);

        Map<Long, Set<String>> collaborationsByProfileId =
                loadCollaborationPreferences(candidates);

        Map<Long, Set<String>> interestsByProfileId =
                loadInterests(candidates);

        Map<Long, List<ProfileExperience>> experiencesByProfileId =
                loadExperiences(candidates);

        Map<Long, List<ExperienceAttribute>> attributesByExperienceId =
                loadExperienceAttributes(experiencesByProfileId);

        /*
         * 2. REQUIRED 기술 필터
         */
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

        /*
         * 3. REQUIRED 경험 필터
         */
        if (project.getExperienceMode() == ProjectConditionMode.REQUIRED) {
            candidates = candidates.stream()
                    .filter(profile ->
                            matchesExperience(
                                    profile,
                                    project,
                                    experiencesByProfileId,
                                    attributesByExperienceId
                            )
                    )
                    .toList();
        }

        /*
         * 4. 각 후보 점수 + 추천 이유 계산
         */
        List<ScoredCandidate> scoredCandidates =
                candidates.stream()
                        .map(profile ->
                                scoreCandidate(
                                        profile,
                                        project,
                                        parsedRole,
                                        recruitment,
                                        rolesByProfileId,
                                        technologiesByProfileId,
                                        collaborationsByProfileId,
                                        interestsByProfileId,
                                        experiencesByProfileId,
                                        attributesByExperienceId
                                )
                        )
                        .sorted(candidateComparator())
                        .toList();

        /*
         * 5. dense rank
         */
        List<RecommendationResponse.Item> items =
                createRankedItems(scoredCandidates);

        return new RecommendationResponse(
                parsedProjectId,
                parsedRole,
                items
        );
    }

    /*
     * 공개 프로필 중 기존 팀원 제외
     */
    private List<Profile> findCandidates(Long projectId) {

        List<Profile> publicProfiles =
                profiles.searchPublicProfiles(
                        null,
                        null,
                        null
                );

        Set<Long> memberProfileIds =
                memberships.findMembers(projectId)
                        .stream()
                        .map(member ->
                                member.getProfile().getId()
                        )
                        .collect(Collectors.toSet());

        return publicProfiles.stream()
                .filter(profile ->
                        !memberProfileIds.contains(
                                profile.getId()
                        )
                )
                .toList();
    }

    /*
     * profileId -> 희망 역할
     *
     * 예:
     * 3 -> [BACKEND]
     * 4 -> [BACKEND, AI]
     */
    private Map<Long, Set<String>> loadRoles(
            List<Profile> candidates
    ) {
        List<Long> ids = profileIds(candidates);

        if (ids.isEmpty()) {
            return Map.of();
        }

        return profileRoles
                .findAllByProfile_IdInOrderByIdAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(
                        role -> role.getProfile().getId(),
                        Collectors.mapping(
                                role -> normalize(role.getRole())
                                        .toUpperCase(Locale.ROOT),
                                Collectors.toSet()
                        )
                ));
    }

    /*
     * profileId -> 기술 목록
     */
    private Map<Long, List<ProfileTechnology>> loadTechnologies(
            List<Profile> candidates
    ) {
        List<Long> ids = profileIds(candidates);

        if (ids.isEmpty()) {
            return Map.of();
        }

        return profileTechnologies
                .findAllByProfile_IdInOrderByIdAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(
                        tech -> tech.getProfile().getId()
                ));
    }

    /*
     * profileId -> COLLAB 선호
     */
    private Map<Long, Set<String>> loadCollaborationPreferences(
            List<Profile> candidates
    ) {
        List<Long> ids = profileIds(candidates);

        if (ids.isEmpty()) {
            return Map.of();
        }

        return profilePreferences
                .findAllByProfile_IdInOrderByIdAsc(ids)
                .stream()
                .filter(preference ->
                        preference.getCategory()
                                == ProfilePreferenceCategory.COLLAB
                )
                .collect(Collectors.groupingBy(
                        preference ->
                                preference.getProfile().getId(),

                        Collectors.mapping(
                                preference ->
                                        normalize(
                                                preference.getValue()
                                        ),
                                Collectors.toSet()
                        )
                ));
    }

    /*
     * profileId -> 관심 분야
     */
    private Map<Long, Set<String>> loadInterests(
            List<Profile> candidates
    ) {
        List<Long> ids = profileIds(candidates);

        if (ids.isEmpty()) {
            return Map.of();
        }

        return profileInterests
                .findAllByProfile_IdInOrderByIdAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(
                        interest ->
                                interest.getProfile().getId(),

                        Collectors.mapping(
                                interest ->
                                        normalize(
                                                interest.getField()
                                        ),
                                Collectors.toSet()
                        )
                ));
    }

    /*
     * profileId -> 경험 목록
     */
    private Map<Long, List<ProfileExperience>> loadExperiences(
            List<Profile> candidates
    ) {
        List<Long> ids = profileIds(candidates);

        if (ids.isEmpty()) {
            return Map.of();
        }

        return profileExperiences
                .findAllByProfile_IdInOrderByIdAsc(ids)
                .stream()
                .collect(Collectors.groupingBy(
                        experience ->
                                experience.getProfile().getId()
                ));
    }

    /*
     * experienceId -> 경험 속성
     *
     * TYPE / FIELD / ROLE / TECHNOLOGY
     */
    private Map<Long, List<ExperienceAttribute>> loadExperienceAttributes(
            Map<Long, List<ProfileExperience>> experiencesByProfileId
    ) {
        List<Long> experienceIds =
                experiencesByProfileId.values()
                        .stream()
                        .flatMap(List::stream)
                        .map(ProfileExperience::getId)
                        .toList();

        if (experienceIds.isEmpty()) {
            return Map.of();
        }

        return experienceAttributes
                .findAllByExperience_IdInOrderByIdAsc(
                        experienceIds
                )
                .stream()
                .collect(Collectors.groupingBy(
                        attribute ->
                                attribute.getExperience().getId()
                ));
    }

    /*
     * REQUIRED 기술 전부 만족 여부
     */
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

        return recruitment.getTechnologies()
                .stream()
                .allMatch(required ->
                        profileTechs.stream()
                                .anyMatch(actual ->
                                        sameTechnology(
                                                actual.getTechnology(),
                                                required.technology()
                                        )
                                                &&
                                                actual.getLevel()
                                                        >= required.minLevel()
                                )
                );
    }

    /*
     * 경험 조건 만족 여부
     */
    private boolean matchesExperience(
            Profile profile,
            Project project,
            Map<Long, List<ProfileExperience>> experiencesByProfileId,
            Map<Long, List<ExperienceAttribute>> attributesByExperienceId
    ) {
        List<ProfileExperience> experiences =
                experiencesByProfileId.getOrDefault(
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
         * 프로젝트가 경험 여부만 보는 경우:
         *
         * type/field 조건이 없으면
         * 경험 하나라도 있으면 충족
         */
        if (requiredTypes.isEmpty()
                && requiredFields.isEmpty()) {
            return true;
        }

        /*
         * 하나의 경험이
         * type + field 조건을 모두 만족하면 통과
         */
        return experiences.stream()
                .anyMatch(experience -> {

                    List<ExperienceAttribute> attributes =
                            attributesByExperienceId
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

                    return typeMatched && fieldMatched;
                });
    }

    /*
     * 실제 후보 하나 평가
     */
    private ScoredCandidate scoreCandidate(
            Profile profile,
            Project project,
            ProjectRole role,
            Recruitment recruitment,
            Map<Long, Set<String>> rolesByProfileId,
            Map<Long, List<ProfileTechnology>> technologiesByProfileId,
            Map<Long, Set<String>> collaborationsByProfileId,
            Map<Long, Set<String>> interestsByProfileId,
            Map<Long, List<ProfileExperience>> experiencesByProfileId,
            Map<Long, List<ExperienceAttribute>> attributesByExperienceId
    ) {
        List<RecommendationResponse.Reason> matched =
                new ArrayList<>();

        List<RecommendationResponse.Reason> unmatched =
                new ArrayList<>();

        /*
         * =====================================
         * STACK
         * =====================================
         */
        int stackScore = 0;

        List<ProfileTechnology> profileTechs =
                technologiesByProfileId.getOrDefault(
                        profile.getId(),
                        List.of()
                );

        if (recruitment.getTechMode()
                != ProjectConditionMode.NONE) {

            for (var condition :
                    recruitment.getTechnologies()) {

                ProfileTechnology matchedTech =
                        profileTechs.stream()
                                .filter(actual ->
                                        sameTechnology(
                                                actual.getTechnology(),
                                                condition.technology()
                                        )
                                )
                                .max(
                                        Comparator.comparingInt(
                                                ProfileTechnology::getLevel
                                        )
                                )
                                .orElse(null);

                if (matchedTech != null
                        &&
                        matchedTech.getLevel()
                                >= condition.minLevel()) {

                    stackScore +=
                            matchedTech.getLevel();

                    matched.add(
                            new RecommendationResponse.Reason(
                                    RecommendationResponse.Category.STACK,
                                    matchedTech.getTechnology()
                                            + " Lv."
                                            + matchedTech.getLevel()
                                            + " 보유"
                            )
                    );

                } else {

                    unmatched.add(
                            new RecommendationResponse.Reason(
                                    RecommendationResponse.Category.STACK,
                                    condition.technology()
                                            + " Lv."
                                            + condition.minLevel()
                                            + " 이상 선호"
                            )
                    );
                }
            }
        }

        /*
         * =====================================
         * COLLAB
         * =====================================
         */
        int collabScore = 0;

        Set<String> profileCollaborations =
                collaborationsByProfileId.getOrDefault(
                        profile.getId(),
                        Set.of()
                );

        List<String> projectCollaborations =
                project.getAttributes()
                        .stream()
                        .filter(attribute ->
                                attribute.category()
                                        == ProjectAttributeCategory.COLLAB
                        )
                        .map(attribute ->
                                attribute.value()
                        )
                        .toList();

        for (String collaboration :
                projectCollaborations) {

            if (profileCollaborations.contains(
                    normalize(collaboration)
            )) {
                collabScore++;

                matched.add(
                        new RecommendationResponse.Reason(
                                RecommendationResponse.Category.COLLAB,
                                collaboration + " 선호"
                        )
                );
            } else {
                unmatched.add(
                        new RecommendationResponse.Reason(
                                RecommendationResponse.Category.COLLAB,
                                collaboration + " 선호 아님"
                        )
                );
            }
        }

        /*
         * =====================================
         * EXPERIENCE
         * =====================================
         */
        int experienceScore = 0;

        if (project.getExperienceMode()
                != ProjectConditionMode.NONE) {

            boolean experienceMatched =
                    matchesExperience(
                            profile,
                            project,
                            experiencesByProfileId,
                            attributesByExperienceId
                    );

            if (experienceMatched) {
                experienceScore = 1;

                matched.add(
                        new RecommendationResponse.Reason(
                                RecommendationResponse.Category.EXPERIENCE,
                                "프로젝트 경험 조건 충족"
                        )
                );

            } else {
                unmatched.add(
                        new RecommendationResponse.Reason(
                                RecommendationResponse.Category.EXPERIENCE,
                                "프로젝트 경험 조건 미충족"
                        )
                );
            }
        }

        /*
         * =====================================
         * ROLE
         * =====================================
         */
        boolean roleMatched =
                rolesByProfileId
                        .getOrDefault(
                                profile.getId(),
                                Set.of()
                        )
                        .contains(role.name());

        int roleScore =
                roleMatched ? 1 : 0;

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

        /*
         * =====================================
         * FIELD
         * =====================================
         */
        int fieldScore = 0;

        Set<String> profileFields =
                interestsByProfileId.getOrDefault(
                        profile.getId(),
                        Set.of()
                );

        List<String> projectFields =
                project.getAttributes()
                        .stream()
                        .filter(attribute ->
                                attribute.category()
                                        == ProjectAttributeCategory.FIELD
                        )
                        .map(attribute ->
                                attribute.value()
                        )
                        .toList();

        for (String field : projectFields) {

            if (profileFields.contains(
                    normalize(field)
            )) {
                fieldScore++;

                matched.add(
                        new RecommendationResponse.Reason(
                                RecommendationResponse.Category.FIELD,
                                field + " 관심"
                        )
                );

            } else {
                unmatched.add(
                        new RecommendationResponse.Reason(
                                RecommendationResponse.Category.FIELD,
                                field + " 관심 정보 없음"
                        )
                );
            }
        }

        return new ScoredCandidate(
                profile,
                new CandidateScore(
                        stackScore,
                        collabScore,
                        experienceScore,
                        roleScore,
                        fieldScore
                ),
                matched,
                unmatched
        );
    }

    /*
     * 추천 우선순위
     *
     * 1. STACK
     * 2. COLLAB
     * 3. EXPERIENCE
     * 4. ROLE
     * 5. FIELD
     */
    private Comparator<ScoredCandidate> candidateComparator() {

        return Comparator
                .comparingInt(
                        (ScoredCandidate candidate) ->
                                candidate.score().stack()
                )
                .reversed()

                .thenComparing(
                        Comparator.comparingInt(
                                (ScoredCandidate candidate) ->
                                        candidate.score().collab()
                        ).reversed()
                )

                .thenComparing(
                        Comparator.comparingInt(
                                (ScoredCandidate candidate) ->
                                        candidate.score().experience()
                        ).reversed()
                )

                .thenComparing(
                        Comparator.comparingInt(
                                (ScoredCandidate candidate) ->
                                        candidate.score().role()
                        ).reversed()
                )

                .thenComparing(
                        Comparator.comparingInt(
                                (ScoredCandidate candidate) ->
                                        candidate.score().field()
                        ).reversed()
                )

                // 완전 동점이면 결과 순서 고정
                .thenComparing(
                        candidate ->
                                candidate.profile().getId()
                );
    }

    /*
     * dense rank
     *
     * 점수:
     * A = 동일
     * B = 동일
     * C = 다음 점수
     *
     * 결과:
     * 1, 1, 2
     */
    private List<RecommendationResponse.Item> createRankedItems(
            List<ScoredCandidate> candidates
    ) {
        List<RecommendationResponse.Item> items =
                new ArrayList<>();

        CandidateScore previousScore = null;

        int rank = 0;

        for (ScoredCandidate candidate :
                candidates) {

            if (!candidate.score()
                    .equals(previousScore)) {

                rank++;

                previousScore =
                        candidate.score();
            }

            items.add(
                    new RecommendationResponse.Item(
                            candidate.profile().getId(),
                            candidate.profile().getName(),
                            rank,
                            candidate.matched(),
                            candidate.unmatched(),
                            null
                    )
            );
        }

        return items;
    }

    private List<Long> profileIds(
            List<Profile> candidates
    ) {
        return candidates.stream()
                .map(Profile::getId)
                .toList();
    }

    private boolean sameTechnology(
            String first,
            String second
    ) {
        return normalize(first)
                .equals(normalize(second));
    }

    private String normalize(
            String value
    ) {
        return value == null
                ? ""
                : value.trim()
                  .toLowerCase(Locale.ROOT);
    }

    private ProjectRole parseRole(
            String role
    ) {
        try {
            return ProjectRole.valueOf(
                    role.trim()
                            .toUpperCase(Locale.ROOT)
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

    /*
     * 점수는 프론트에 보내지 않는다.
     * 내부 순위 계산용.
     */
    private record CandidateScore(
            int stack,
            int collab,
            int experience,
            int role,
            int field
    ) {}

    private record ScoredCandidate(
            Profile profile,
            CandidateScore score,
            List<RecommendationResponse.Reason> matched,
            List<RecommendationResponse.Reason> unmatched
    ) {}
}