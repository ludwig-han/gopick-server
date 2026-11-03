package com.gopick.server.profile.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProfileTechnologyRequest {
    @NotBlank
    private String technology;

    @NotNull
    @Min(1)
    @Max(4)
    private Integer level;

    public String getTechnology() {
        return technology;
    }


    public Integer getLevel() {
        return level;
    }

    public void setTechnology(String technology) {
        this.technology = technology;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }
}
