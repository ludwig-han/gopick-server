package com.gopick.server.project;

import com.gopick.server.account.domain.Account;
import com.gopick.server.profile.domain.Profile;
import com.gopick.server.project.domain.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

abstract class ProjectApiFixture {
    @Autowired protected MockMvc mvc;
    @Autowired protected EntityManager em;
    protected Account leader, member, outsider;
    protected Profile leaderProfile, memberProfile;
    protected Project project;
    @BeforeEach
    void setUp() {
        leader = persist(new Account("leader@example.com", "encoded-secret"));
        member = persist(new Account("member@example.com", "encoded-secret"));
        outsider = persist(new Account("outsider@example.com", "encoded-secret"));
        leaderProfile = profile(leader, true); memberProfile = profile(member, false);
        project = newProject("GoPick", ProjectStatus.PREPARING, true);
        persist(new ProjectMember(project, leaderProfile, ProjectMemberType.LEADER, ProjectRole.BACKEND));
        persist(new ProjectMember(project, memberProfile, ProjectMemberType.MEMBER, ProjectRole.FRONTEND));
        em.flush();
    }
    protected String path() { return "/projects/" + project.getId(); }
    protected Project newProject(String title, ProjectStatus status, boolean recruiting) {
        var result = new Project("extra", "wish", "goal", "background", "description", title, recruiting);
        result.setStatus(status); return persist(result);
    }
    protected Profile profile(Account account, boolean isPublic) {
        return persist(new Profile(account, account.getEmail().split("@")[0], "CS", "DISCORD", "contact-secret", isPublic, "FOLLOW", "intro"));
    }
    protected MockHttpSession session(Account account) {
        var session = new MockHttpSession(); session.setAttribute("accountId", account.getId()); return session;
    }
    protected <T> T persist(T value) { em.persist(value); return value; }
}
