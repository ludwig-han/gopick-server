package com.gopick.server.profile.dto;

import java.util.ArrayList;
import java.util.List;

public class ProfilePreferencesRequest {

    private List<String> collaboration = new ArrayList<>();
    private List<String> styles = new ArrayList<>();
    private List<String> strengths = new ArrayList<>();
    private List<String> projectTendencies = new ArrayList<>();
    private List<String> communication = new ArrayList<>();

    // getters / setters

    public List<String> getCollaboration() {
        return collaboration;
    }

    public void setCollaboration(List<String> collaboration) {
        this.collaboration = collaboration;
    }

    public List<String> getStyles() {
        return styles;
    }

    public void setStyles(List<String> styles) {
        this.styles = styles;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getProjectTendencies() {
        return projectTendencies;
    }

    public void setProjectTendencies(List<String> projectTendencies) {
        this.projectTendencies = projectTendencies;
    }

    public List<String> getCommunication() {
        return communication;
    }

    public void setCommunication(List<String> communication) {
        this.communication = communication;
    }
}