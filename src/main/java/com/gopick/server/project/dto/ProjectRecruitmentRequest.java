package com.gopick.server.project.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.gopick.server.project.dto.ProjectRecruitmentDetails.Attribute;
import com.gopick.server.project.dto.ProjectRecruitmentDetails.Experience;
import com.gopick.server.project.dto.ProjectRecruitmentDetails.Link;
import com.gopick.server.project.dto.ProjectRecruitmentDetails.RoleRecruitment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public abstract class ProjectRecruitmentRequest {
    private List<@NotNull @Valid Attribute> attributes;
    @Valid
    private Experience experience;
    private List<@NotNull @Valid RoleRecruitment> recruitments;
    private List<@NotNull @Valid Link> links;

    public List<Attribute> getAttributes() {
        return attributes;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setAttributes(List<Attribute> attributes) {
        this.attributes = attributes;
    }

    public Experience getExperience() {
        return experience;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setExperience(Experience experience) {
        this.experience = experience;
    }

    public List<RoleRecruitment> getRecruitments() {
        return recruitments;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setRecruitments(List<RoleRecruitment> recruitments) {
        this.recruitments = recruitments;
    }

    public List<Link> getLinks() {
        return links;
    }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setLinks(List<Link> links) {
        this.links = links;
    }
}
