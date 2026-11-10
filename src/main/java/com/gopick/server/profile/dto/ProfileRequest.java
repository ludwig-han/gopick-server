package com.gopick.server.profile.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ProfileRequest {

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

    private String teamTendency;
    private String intro;

    @NotEmpty
    private List<String> parts = new ArrayList<>();

    @NotEmpty
    private List<String> interests = new ArrayList<>();

    @NotEmpty
    private List<String> preferredRoles = new ArrayList<>();

    @Valid
    @NotEmpty
    private List<ProfileTechnologyRequest> technologies = new ArrayList<>();

    @Valid
    private ProfilePreferencesRequest preferences = new ProfilePreferencesRequest();

    @Valid
    private List<ProfileExperienceRequest> experiences = new ArrayList<>();

    // getters / setters

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

    public List<String> getPreferredRoles() {
        return preferredRoles;
    }

    public void setPreferredRoles(List<String> preferredRoles) {
        this.preferredRoles = preferredRoles;
    }

    public List<ProfileTechnologyRequest> getTechnologies() {
        return technologies;
    }

    public void setTechnologies(List<ProfileTechnologyRequest> technologies) {
        this.technologies = technologies;
    }

    public ProfilePreferencesRequest getPreferences() {
        return preferences;
    }

    public void setPreferences(ProfilePreferencesRequest preferences) {
        this.preferences = preferences;
    }

    public List<ProfileExperienceRequest> getExperiences() {
        return experiences;
    }

    public void setExperiences(List<ProfileExperienceRequest> experiences) {
        this.experiences = experiences;
    }

    public boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }
}