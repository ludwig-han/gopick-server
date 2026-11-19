package com.gopick.server.project.service;

import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.dto.RecommendationAiRequest;
import com.gopick.server.project.dto.RecommendationAiResult;
import com.gopick.server.project.dto.RecommendationResponse;

import java.util.List;
import java.util.Locale;

/** Applies the no-experience-condition policy independently of the AI provider. */
final class RecommendationExperiencePolicy {
    private RecommendationExperiencePolicy() {}

    static boolean ignoresExperience(RecommendationAiRequest request) {
        return request.project().experienceMode() == null
                || request.project().experienceMode() == ProjectConditionMode.NONE;
    }

    static RecommendationAiRequest sanitizeRequest(RecommendationAiRequest request) {
        if (!ignoresExperience(request)) return request;
        var candidates = request.candidates().stream().map(c -> new RecommendationAiRequest.Candidate(
                c.profileId(), c.name(), c.major(), c.teamTendency(), c.intro(), c.parts(),
                c.preferredRoles(), c.technologies(), c.interests(), c.preferences(), List.of())).toList();
        return new RecommendationAiRequest(request.project(), candidates);
    }

    static RecommendationAiResult sanitizeResult(RecommendationAiRequest request, RecommendationAiResult result) {
        if (!ignoresExperience(request) || result == null || result.items() == null) return result;
        return new RecommendationAiResult(result.items().stream().map(item -> {
            if (item == null) return null;
            var matched = safeReasons(item.matched());
            var unmatched = safeReasons(item.unmatched());
            String summary = "일치 항목 %d개, 확인이 필요한 항목 %d개입니다.".formatted(matched.size(), unmatched.size());
            return new RecommendationAiResult.Item(item.profileId(), item.score(), matched, unmatched, summary);
        }).toList());
    }

    private static List<RecommendationResponse.Reason> safeReasons(List<RecommendationResponse.Reason> reasons) {
        if (reasons == null) return List.of();
        return reasons.stream().filter(reason -> reason != null
                && reason.category() != RecommendationResponse.Category.EXPERIENCE
                && !mentionsExperience(reason.text())).toList();
    }

    private static boolean mentionsExperience(String text) {
        if (text == null) return false;
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("경험") || normalized.contains("경력")
                || normalized.contains("experience") || normalized.contains("career");
    }
}
