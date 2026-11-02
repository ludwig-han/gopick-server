package com.gopick.server.account.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true)
    String email;

    @Column(nullable = false)
    String password;

    @Column(nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist     // DB에 insert 되기 직전에 실행됨
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    protected Account() {}

    public Account(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
