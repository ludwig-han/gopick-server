package com.gopick.server.bookmark.domain;

import com.gopick.server.account.domain.Account;
import com.gopick.server.profile.domain.Profile;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "profile_bookmark", uniqueConstraints = @UniqueConstraint(
        name = "uk_profile_bookmark_owner_target", columnNames = {"account_id", "profile_id"}))
public class ProfileBookmark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Profile profile;

    protected ProfileBookmark() {}

    public ProfileBookmark(Account account, Profile profile) {
        this.account = account;
        this.profile = profile;
    }

    public Profile getProfile() {
        return profile;
    }
}
