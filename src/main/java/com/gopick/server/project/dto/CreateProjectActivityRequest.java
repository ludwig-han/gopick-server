package com.gopick.server.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectActivityRequest(
        @NotBlank @Size(max = 255) @Pattern(regexp = "^[^\\r\\n]*$") String content) {}
