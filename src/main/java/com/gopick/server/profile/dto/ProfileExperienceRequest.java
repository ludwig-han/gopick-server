package com.gopick.server.profile.dto;

import java.util.ArrayList;
import java.util.List;

public class ProfileExperienceRequest {

    private List<String> types = new ArrayList<>();
    private List<String> fields = new ArrayList<>();
    private List<String> roles = new ArrayList<>();
    private List<String> technologies = new ArrayList<>();

    private String description;

    // getters / setters

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public List<String> getTechnologies() {
        return technologies;
    }

    public void setTechnologies(List<String> technologies) {
        this.technologies = technologies;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}