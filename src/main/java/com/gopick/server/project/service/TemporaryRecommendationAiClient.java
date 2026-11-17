package com.gopick.server.project.service;

import com.gopick.server.profile.domain.ExperienceAttributeCategory;
import com.gopick.server.profile.domain.ProfilePreferenceCategory;
import com.gopick.server.project.domain.ProjectAttributeCategory;
import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.dto.RecommendationAiRequest;
import com.gopick.server.project.dto.RecommendationAiResult;
import com.gopick.server.project.dto.RecommendationResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/*
 * 임시 테스트용.
 *
 * 실제 AI가 아님.
 *
 * 학교 AI 연동 후
 * recommendation.ai.mode=school 로 바꾸고
 * SchoolRecommendationAiClient가 대신 동작하도록 한다.
 */
@Component
@ConditionalOnProperty(
        name = "recommendation.ai.mode",
        havingValue = "stub",
        matchIfMissing = true
)
public class TemporaryRecommendationAiClient
        implements RecommendationAiClient {

    @Override
    public RecommendationAiResult recommend(
            RecommendationAiRequest request
    ) {
        List<RecommendationAiResult.Item> items =
                request.candidates()
                        .stream()
                        .map(candidate ->
                                evaluate(
                                        request.project(),
                                        candidate
                                )
                        )
                        .toList();

        return new RecommendationAiResult(
                items
        );
    }

    private RecommendationAiResult.Item evaluate(
            RecommendationAiRequest.ProjectData project,
            RecommendationAiRequest.Candidate candidate
    ) {
        int score = 0;

        var matched =
                new ArrayList<RecommendationResponse.Reason>();

        var unmatched =
                new ArrayList<RecommendationResponse.Reason>();

        /*
         * STACK
         */
        if (project.techMode()
                != ProjectConditionMode.NONE) {

            for (var condition :
                    project.technologies()) {

                var tech =
                        candidate.technologies()
                                .stream()
                                .filter(actual ->
                                        normalize(
                                                actual.technology()
                                        ).equals(
                                                normalize(
                                                        condition
                                                                .technology()
                                                )
                                        )
                                )
                                .max(
                                        java.util.Comparator
                                                .comparingInt(
                                                        RecommendationAiRequest
                                                                .Technology::level
                                                )
                                )
                                .orElse(null);

                if (tech != null
                        &&
                        tech.level()
                                >= condition.minLevel()) {

                    score +=
                            tech.level() * 10_000;

                    matched.add(
                            new RecommendationResponse.Reason(
                                    RecommendationResponse.Category.STACK,
                                    tech.technology()
                                            + " Lv."
                                            + tech.level()
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
         * COLLAB
         */
        Set<String> candidateCollabs =
                candidate.preferences()
                        .stream()
                        .filter(preference ->
                                preference.category()
                                        == ProfilePreferenceCategory.COLLAB
                        )
                        .map(preference ->
                                normalize(
                                        preference.value()
                                )
                        )
                        .collect(Collectors.toSet());

        for (var attribute :
                project.attributes()) {

            if (attribute.category()
                    != ProjectAttributeCategory.COLLAB) {
                continue;
            }

            if (candidateCollabs.contains(
                    normalize(
                            attribute.value()
                    )
            )) {
                score += 1_000;

                matched.add(
                        new RecommendationResponse.Reason(
                                RecommendationResponse.Category.COLLAB,
                                attribute.value()
                                        + " 선호"
                        )
                );

            } else {
                unmatched.add(
                        new RecommendationResponse.Reason(
                                RecommendationResponse.Category.COLLAB,
                                attribute.value()
                                        + " 선호 아님"
                        )
                );
            }
        }

        /*
         * EXPERIENCE
         */
        if (project.experienceMode()
                != ProjectConditionMode.NONE) {

            boolean experienceMatched =
                    experienceMatches(
                            project,
                            candidate
                    );

            if (experienceMatched) {
                score += 100;

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
         * ROLE
         */
        boolean roleMatched =
                candidate.preferredRoles()
                        .stream()
                        .map(this::normalize)
                        .anyMatch(value ->
                                value.equals(
                                        normalize(
                                                project.role()
                                                        .name()
                                        )
                                )
                        );

        if (roleMatched) {
            score += 10;

            matched.add(
                    new RecommendationResponse.Reason(
                            RecommendationResponse.Category.ROLE,
                            project.role().name()
                                    + " 희망"
                    )
            );

        } else {
            unmatched.add(
                    new RecommendationResponse.Reason(
                            RecommendationResponse.Category.ROLE,
                            project.role().name()
                                    + " 희망 아님"
                    )
            );
        }

        /*
         * FIELD
         */
        Set<String> interests =
                candidate.interests()
                        .stream()
                        .map(this::normalize)
                        .collect(Collectors.toSet());

        for (var attribute :
                project.attributes()) {

            if (attribute.category()
                    != ProjectAttributeCategory.FIELD) {
                continue;
            }

            if (interests.contains(
                    normalize(
                            attribute.value()
                    )
            )) {
                score++;

                matched.add(
                        new RecommendationResponse.Reason(
                                RecommendationResponse.Category.FIELD,
                                attribute.value()
                                        + " 관심"
                        )
                );

            } else {
                unmatched.add(
                        new RecommendationResponse.Reason(
                                RecommendationResponse.Category.FIELD,
                                attribute.value()
                                        + " 관심 정보 없음"
                        )
                );
            }
        }

        return new RecommendationAiResult.Item(
                candidate.profileId(),
                score,
                matched,
                unmatched,
                null
        );
    }

    private boolean experienceMatches(
            RecommendationAiRequest.ProjectData project,
            RecommendationAiRequest.Candidate candidate
    ) {
        if (candidate.experiences()
                .isEmpty()) {
            return false;
        }

        Set<String> requiredTypes =
                project.experienceTypes()
                        .stream()
                        .map(this::normalize)
                        .collect(Collectors.toSet());

        Set<String> requiredFields =
                project.experienceFields()
                        .stream()
                        .map(this::normalize)
                        .collect(Collectors.toSet());

        if (requiredTypes.isEmpty()
                && requiredFields.isEmpty()) {
            return true;
        }

        return candidate.experiences()
                .stream()
                .anyMatch(experience -> {

                    Set<String> types =
                            experience.attributes()
                                    .stream()
                                    .filter(attribute ->
                                            attribute.category()
                                                    == ExperienceAttributeCategory.TYPE
                                    )
                                    .map(attribute ->
                                            normalize(
                                                    attribute.value()
                                            )
                                    )
                                    .collect(Collectors.toSet());

                    Set<String> fields =
                            experience.attributes()
                                    .stream()
                                    .filter(attribute ->
                                            attribute.category()
                                                    == ExperienceAttributeCategory.FIELD
                                    )
                                    .map(attribute ->
                                            normalize(
                                                    attribute.value()
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

    private String normalize(
            String value
    ) {
        return value == null
                ? ""
                : value.trim()
                  .toLowerCase(Locale.ROOT);
    }
}