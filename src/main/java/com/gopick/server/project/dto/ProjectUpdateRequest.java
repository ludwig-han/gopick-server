package com.gopick.server.project.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.gopick.server.project.exception.ProjectValidationException;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ProjectUpdateRequest {
    @Size(max = 255)
    @Pattern(regexp = "(?s).*\\S.*", message = "제목은 비어 있을 수 없습니다.")
    private String title;
    @Size(max = 255)
    private String description;
    @Size(max = 255)
    private String background;
    @Size(max = 255)
    private String goal;
    @Size(max = 255)
    private String wish;
    @Size(max = 255)
    private String extra;
    private Boolean recruiting;
    public String getTitle() { return title; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setDescription(String description) { this.description = description; }
    public String getBackground() { return background; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setBackground(String background) { this.background = background; }
    public String getGoal() { return goal; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setGoal(String goal) { this.goal = goal; }
    public String getWish() { return wish; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setWish(String wish) { this.wish = wish; }
    public String getExtra() { return extra; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setExtra(String extra) { this.extra = extra; }
    public Boolean getRecruiting() { return recruiting; }
    @JsonSetter(nulls = Nulls.FAIL)
    public void setRecruiting(Boolean recruiting) { this.recruiting = recruiting; }
    @JsonAnySetter
    public void rejectUnsupportedField(String field, Object value) {
        throw new ProjectValidationException(field, "현재 지원하지 않는 수정 필드입니다.");
    }
}
