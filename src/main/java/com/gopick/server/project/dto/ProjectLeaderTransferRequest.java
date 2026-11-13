package com.gopick.server.project.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProjectLeaderTransferRequest(@NotNull @Positive Long targetProfileId) {}
