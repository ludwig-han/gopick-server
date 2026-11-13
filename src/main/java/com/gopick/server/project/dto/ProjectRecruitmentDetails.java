package com.gopick.server.project.dto;

import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectAttributeCategory;
import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.domain.Recruitment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProjectRecruitmentDetails(
        List<Attribute> attributes,
        Experience experience,
        List<RoleRecruitment> recruitments,
        List<Link> links
) {
    public record Attribute(
            @NotNull ProjectAttributeCategory category,
            @NotBlank @Size(max = 255) String value
    ) {}

    public record Experience(
            @NotNull ProjectConditionMode mode,
            List<@NotBlank @Size(max = 255) String> types,
            List<@NotBlank @Size(max = 255) String> fields
    ) {
        public Experience {
            types = types == null ? List.of() : types;
            fields = fields == null ? List.of() : fields;
        }
    }

    public record RoleRecruitment(
            @NotNull ProjectRole role,
            @NotNull @PositiveOrZero Integer remainingCount,
            @NotNull ProjectConditionMode techMode,
            List<@NotNull @Valid Technology> technologies
    ) {
        public RoleRecruitment {
            technologies = technologies == null ? List.of() : technologies;
        }
    }

    public record Technology(
            @NotBlank @Size(max = 255) String technology,
            @NotNull @Min(1) @Max(4) Integer minLevel
    ) {}

    public record Link(
            @NotBlank @Size(max = 255) String type,
            @NotBlank @Size(max = 2048) String url
    ) {}

    public static ProjectRecruitmentDetails from(Project project, List<Recruitment> roleRecruitments) {
        Experience experience = new Experience(
                project.getExperienceMode() == null ? ProjectConditionMode.NONE : project.getExperienceMode(),
                List.copyOf(project.getExperienceTypes()), List.copyOf(project.getExperienceFields()));
        return new ProjectRecruitmentDetails(
                project.getAttributes().stream().map(a -> new Attribute(a.category(), a.value())).toList(),
                experience,
                roleRecruitments.stream()
                        .map(recruitment -> new RoleRecruitment(
                                recruitment.getRole(), recruitment.getRemainingCount(), recruitment.getTechMode(),
                                recruitment.getTechnologies().stream()
                                        .map(t -> new Technology(t.technology(), t.minLevel()))
                                        .toList()))
                        .toList(),
                project.getLinks().stream().map(link -> new Link(link.type(), link.url())).toList());
    }
}
