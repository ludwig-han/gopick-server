package com.gopick.server.bookmark.controller;

import com.gopick.server.bookmark.dto.ProfileBookmarkResponse;
import com.gopick.server.bookmark.service.BookmarkService;
import com.gopick.server.project.controller.ProjectApiEndpoint;
import com.gopick.server.project.dto.ProjectResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
@ProjectApiEndpoint
public class BookmarkController {
    private final BookmarkService bookmarks;

    public BookmarkController(BookmarkService bookmarks) {
        this.bookmarks = bookmarks;
    }

    @GetMapping("/me/bookmarked-profiles")
    public List<ProfileBookmarkResponse> listProfileBookmarks(HttpServletRequest request) {
        return bookmarks.profileBookmarks(requireAccountId(request));
    }

    @PostMapping("/profiles/{targetProfileId}/bookmarks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addProfileBookmark(@PathVariable("targetProfileId") Long profileId, HttpServletRequest request) {
        bookmarks.addProfileBookmark(requireAccountId(request), profileId);
    }

    @DeleteMapping("/profiles/{targetProfileId}/bookmarks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeProfileBookmark(@PathVariable("targetProfileId") Long profileId, HttpServletRequest request) {
        bookmarks.removeProfileBookmark(requireAccountId(request), profileId);
    }

    @GetMapping("/me/scrapped-projects")
    public List<ProjectResponse> listProjectScraps(HttpServletRequest request) {
        return bookmarks.projectScraps(requireAccountId(request));
    }

    @PostMapping("/projects/{projectId}/scraps")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addProjectScrap(@PathVariable("projectId") Long projectId, HttpServletRequest request) {
        bookmarks.addProjectScrap(requireAccountId(request), projectId);
    }

    @DeleteMapping("/projects/{projectId}/scraps")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeProjectScrap(@PathVariable("projectId") Long projectId, HttpServletRequest request) {
        bookmarks.removeProjectScrap(requireAccountId(request), projectId);
    }
}
