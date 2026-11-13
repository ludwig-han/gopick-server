package com.gopick.server.bookmark.service;

import com.gopick.server.account.domain.Account;
import com.gopick.server.account.exception.UnauthenticatedException;
import com.gopick.server.account.repository.AccountRepository;
import com.gopick.server.bookmark.domain.ProfileBookmark;
import com.gopick.server.bookmark.domain.ProjectScrap;
import com.gopick.server.bookmark.dto.ProfileBookmarkResponse;
import com.gopick.server.bookmark.repository.ProfileBookmarkRepository;
import com.gopick.server.bookmark.repository.ProjectScrapRepository;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.profile.exception.PrivateProfileException;
import com.gopick.server.profile.exception.ProfileNotFoundException;
import com.gopick.server.profile.repository.ProfileRepository;
import com.gopick.server.project.domain.Project;
import com.gopick.server.project.dto.ProjectResponse;
import com.gopick.server.project.exception.ProjectNotFoundException;
import com.gopick.server.project.repository.ProjectRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class BookmarkService {
    private final AccountRepository accounts;
    private final ProfileRepository profiles;
    private final ProjectRepository projects;
    private final ProfileBookmarkRepository bookmarks;
    private final ProjectScrapRepository scraps;
    private final EntityManager entityManager;

    public BookmarkService(AccountRepository accounts, ProfileRepository profiles, ProjectRepository projects,
                           ProfileBookmarkRepository bookmarks, ProjectScrapRepository scraps,
                           EntityManager entityManager) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.projects = projects;
        this.bookmarks = bookmarks;
        this.scraps = scraps;
        this.entityManager = entityManager;
    }

    public void addProfileBookmark(Long accountId, Long profileId) {
        Account owner = lockAccount(accountId);
        if (bookmarks.existsByAccount_IdAndProfile_Id(accountId, profileId)) {
            return;
        }

        Profile profile = profiles.findById(profileId).orElseThrow(ProfileNotFoundException::new);
        if (!profile.isPublic()) {
            throw new PrivateProfileException();
        }
        bookmarks.save(new ProfileBookmark(owner, profile));
    }

    public void removeProfileBookmark(Long accountId, Long profileId) {
        lockAccount(accountId);
        bookmarks.deleteByAccount_IdAndProfile_Id(accountId, profileId);
    }

    @Transactional(readOnly = true)
    public List<ProfileBookmarkResponse> profileBookmarks(Long accountId) {
        requireAccount(accountId);
        return bookmarks.findAllByAccount_IdOrderByIdDesc(accountId).stream()
                .map(bookmark -> ProfileBookmarkResponse.from(bookmark.getProfile()))
                .toList();
    }

    public void addProjectScrap(Long accountId, Long projectId) {
        Account owner = lockAccount(accountId);
        if (scraps.existsByAccount_IdAndProject_Id(accountId, projectId)) {
            return;
        }

        Project project = projects.findById(projectId).orElseThrow(ProjectNotFoundException::new);
        scraps.save(new ProjectScrap(owner, project));
    }

    public void removeProjectScrap(Long accountId, Long projectId) {
        lockAccount(accountId);
        scraps.deleteByAccount_IdAndProject_Id(accountId, projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> projectScraps(Long accountId) {
        requireAccount(accountId);
        return scraps.findAllByAccount_IdOrderByIdDesc(accountId).stream()
                .map(scrap -> ProjectResponse.from(scrap.getProject()))
                .toList();
    }

    private void requireAccount(Long accountId) {
        if (accountId == null || !accounts.existsById(accountId)) {
            throw new UnauthenticatedException();
        }
    }

    private Account lockAccount(Long accountId) {
        Account account = accountId == null ? null
                : entityManager.find(Account.class, accountId, LockModeType.PESSIMISTIC_WRITE);
        if (account == null) {
            throw new UnauthenticatedException();
        }
        return account;
    }
}
