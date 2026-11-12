package com.gopick.server.profile.controller;

import com.gopick.server.profile.dto.ProfileSearchResponse;
import com.gopick.server.profile.service.ProfileSearchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.gopick.server.global.SessionUtil.requireAccountId;

@RestController
public class ProfileSearchController {
    private final ProfileSearchService profileSearchService;

    public ProfileSearchController(ProfileSearchService profileSearchService) {
        this.profileSearchService = profileSearchService;
    }

    @GetMapping("/profiles")
    public List<ProfileSearchResponse> searchProfiles(
            @RequestParam(name = "technology", required = false) String technology,
            @RequestParam(name = "interest", required = false) String interest,
            @RequestParam(name = "preferredRole", required = false) String preferredRole,
            HttpServletRequest request) {
        return profileSearchService.search(requireAccountId(request), technology, interest, preferredRole);
    }
}
