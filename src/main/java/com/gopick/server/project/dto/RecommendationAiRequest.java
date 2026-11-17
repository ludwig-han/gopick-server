package com.gopick.server.project.dto;

import com.gopick.server.profile.domain.ExperienceAttributeCategory;
import com.gopick.server.profile.domain.ProfilePreferenceCategory;
import com.gopick.server.project.domain.ProjectAttributeCategory;
import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectRole;

import java.util.List;

public record RecommendationAiRequest(
        ProjectData project,
        List<Candidate> candidates
) {

    public record ProjectData(
            Long projectId,
            ProjectRole role,

            String title,
            String description,
            String background,
            String goal,
            String wish,

            ProjectConditionMode techMode,
            List<TechnologyCondition> technologies,

            ProjectConditionMode experienceMode,
            List<String> experienceTypes,
            List<String> experienceFields,

            List<Attribute> attributes
    ) {}

    public record TechnologyCondition(
            String technology,
            int minLevel
    ) {}

    public record Attribute(
            ProjectAttributeCategory category,
            String value
    ) {}

    public record Candidate(
            Long profileId,
            String name,
            String major,
            String teamTendency,
            String intro,

            List<String> parts,
            List<String> preferredRoles,
            List<Technology> technologies,
            List<String> interests,
            List<Preference> preferences,
            List<Experience> experiences
    ) {}

    public record Technology(
            String technology,
            int level
    ) {}

    public record Preference(
            ProfilePreferenceCategory category,
            String value
    ) {}

    public record Experience(
            String description,
            List<ExperienceAttribute> attributes
    ) {}

    public record ExperienceAttribute(
            ExperienceAttributeCategory category,
            String value
    ) {}
}