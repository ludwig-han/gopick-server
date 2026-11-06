package com.gopick.server.profile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CreateProfileRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String major;

    @NotBlank
    private String contactPlatform;

    @NotBlank
    private String contactId;

    @NotNull
    private Boolean isPublic;

    @NotBlank
    private String teamTendency;

    @NotBlank
    private String intro;

    private List<String> parts = new ArrayList<>();
    private List<String> interests = new ArrayList<>();
    private List<String> roles = new ArrayList<>();

    @Valid
    private List<ProfileTechnologyRequest> technologies = new ArrayList<>();

    public CreateProfileRequest() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getContactPlatform() {
        return contactPlatform;
    }

    public void setContactPlatform(String contactPlatform) {
        this.contactPlatform = contactPlatform;
    }

    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public String getTeamTendency() {
        return teamTendency;
    }

    public void setTeamTendency(String teamTendency) {
        this.teamTendency = teamTendency;
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        this.intro = intro;
    }

    public List<String> getParts() {
        return parts;
    }

    public void setParts(List<String> parts) {
        this.parts = parts;
    }

    public List<String> getInterests() {
        return interests;
    }

    public void setInterests(List<String> interests) {
        this.interests = interests;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<ProfileTechnologyRequest> getTechnologies() {
        return technologies;
    }

    public void setTechnologies(List<ProfileTechnologyRequest> technologies) {
        this.technologies = technologies;
    }
}
