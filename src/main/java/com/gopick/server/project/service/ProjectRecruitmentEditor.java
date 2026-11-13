package com.gopick.server.project.service;

import com.gopick.server.project.domain.Project;
import com.gopick.server.project.domain.ProjectAttribute;
import com.gopick.server.project.domain.ProjectConditionMode;
import com.gopick.server.project.domain.ProjectLink;
import com.gopick.server.project.domain.ProjectRole;
import com.gopick.server.project.domain.Recruitment;
import com.gopick.server.project.domain.RecruitmentTechnology;
import com.gopick.server.project.dto.ProjectRecruitmentDetails.Experience;
import com.gopick.server.project.dto.ProjectRecruitmentDetails.Link;
import com.gopick.server.project.dto.ProjectRecruitmentDetails.RoleRecruitment;
import com.gopick.server.project.dto.ProjectRecruitmentRequest;
import com.gopick.server.project.exception.ProjectValidationException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
class ProjectRecruitmentEditor {
    void apply(Project project, ProjectRecruitmentRequest request) {
        if (request.getRecruitments() != null) {
            validateRecruitments(request.getRecruitments());
        }
        Experience experience = request.getExperience();
        if (experience != null && experience.mode() == ProjectConditionMode.NONE
                && (!experience.types().isEmpty() || !experience.fields().isEmpty())) {
            throw new ProjectValidationException("experience", "경험 조건이 NONE이면 유형과 분야를 비워 주세요.");
        }
        if (request.getLinks() != null) {
            validateLinks(request.getLinks());
        }

        if (request.getAttributes() != null) {
            project.getAttributes().clear();
            project.getAttributes().addAll(request.getAttributes().stream()
                    .map(attribute -> new ProjectAttribute(attribute.category(), attribute.value()))
                    .toList());
        }
        if (experience != null) {
            project.updateExperience(experience.mode(), experience.types(), experience.fields());
        }
        if (request.getRecruitments() != null) {
            replaceRecruitments(project, request.getRecruitments());
        }
        if (request.getLinks() != null) {
            project.getLinks().clear();
            project.getLinks().addAll(request.getLinks().stream()
                    .map(link -> new ProjectLink(link.type(), link.url()))
                    .toList());
        }
    }

    private void validateRecruitments(List<RoleRecruitment> recruitments) {
        Set<ProjectRole> roles = EnumSet.noneOf(ProjectRole.class);
        for (int i = 0; i < recruitments.size(); i++) {
            RoleRecruitment recruitment = recruitments.get(i);
            String field = "recruitments[" + i + "]";
            if (!roles.add(recruitment.role())) {
                throw new ProjectValidationException(field + ".role", "같은 모집 역할은 한 번만 등록할 수 있습니다.");
            }
            boolean usesTechnology = recruitment.techMode() != ProjectConditionMode.NONE;
            if (usesTechnology == recruitment.technologies().isEmpty()) {
                throw new ProjectValidationException(field + ".technologies",
                        "필수·우대 조건에는 기술을 입력하고, NONE에는 기술을 비워 주세요.");
            }
            Set<String> names = new HashSet<>();
            for (var technology : recruitment.technologies()) {
                if (!names.add(technology.technology().strip().toLowerCase(Locale.ROOT))) {
                    throw new ProjectValidationException(field + ".technologies",
                            "같은 모집 역할에 기술을 중복해서 등록할 수 없습니다.");
                }
            }
        }
    }

    private void validateLinks(List<Link> links) {
        for (int i = 0; i < links.size(); i++) {
            try {
                URI url = new URI(links.get(i).url());
                if (url.getHost() != null
                        && ("https".equalsIgnoreCase(url.getScheme()) || "http".equalsIgnoreCase(url.getScheme()))) {
                    continue;
                }
            } catch (URISyntaxException ignored) {
                // Malformed and unsupported URLs use the same validation response.
            }
            throw new ProjectValidationException("links[" + i + "].url", "유효한 HTTP 또는 HTTPS 주소를 입력해 주세요.");
        }
    }

    private void replaceRecruitments(Project project, List<RoleRecruitment> requested) {
        Map<ProjectRole, Recruitment> existing = new EnumMap<>(ProjectRole.class);
        project.getRecruitments().forEach(recruitment -> existing.put(recruitment.getRole(), recruitment));
        for (RoleRecruitment entry : requested) {
            List<RecruitmentTechnology> technologies = entry.technologies().stream()
                    .map(technology -> new RecruitmentTechnology(technology.technology().strip(), technology.minLevel()))
                    .toList();
            Recruitment recruitment = existing.remove(entry.role());
            if (recruitment == null) {
                project.getRecruitments().add(new Recruitment(project, entry.role(), entry.remainingCount(),
                        entry.techMode(), technologies));
            } else {
                recruitment.update(entry.remainingCount(), entry.techMode(), technologies);
            }
        }
        project.getRecruitments().removeAll(existing.values());
    }
}
