package com.gopick.server.account.dto;

import java.time.LocalDateTime;

public class CreateAccountResponse {
    private final Long id;
    private final String email;
    private final LocalDateTime createdAt;

    public CreateAccountResponse(Long id, String email, LocalDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.createdAt = createdAt;
    }

    public String getEmail() {
        return email;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
