package com.gopick.server.account.service;

import com.gopick.server.account.domain.Account;
import com.gopick.server.account.exception.UnauthenticatedException;
import com.gopick.server.profile.repository.ProfileRepository;
import com.gopick.server.profile.service.ProfileService;
import com.gopick.server.project.repository.ProjectMemberRepository;
import com.gopick.server.project.repository.ProjectRepository;
import com.gopick.server.notification.NotificationService;
import com.gopick.server.notification.NotificationType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AccountWithdrawalService {
    private final EntityManager em;
    private final ProfileRepository profiles;
    private final ProfileService profileService;
    private final ProjectMemberRepository members;
    private final ProjectRepository projects;
    private final NotificationService notifications;

    public AccountWithdrawalService(EntityManager em, ProfileRepository profiles, ProfileService profileService,
                                    ProjectMemberRepository members, ProjectRepository projects, NotificationService notifications) {
        this.em = em;
        this.profiles = profiles;
        this.profileService = profileService;
        this.members = members;
        this.projects = projects;
        this.notifications = notifications;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(Long accountId) {
        Account account = em.find(Account.class, accountId, LockModeType.PESSIMISTIC_WRITE);
        if (account == null) throw new UnauthenticatedException();
        var profile = profiles.findByAccount_Id(accountId).orElse(null);
        if (profile != null) {
            Long profileId = profile.getId();
            // Include soft-deleted projects when cleaning up their references.
            List<?> projectIds = em.createNativeQuery("select project_id from project_member where profile_id = :id order by project_id")
                    .setParameter("id", profileId).getResultList();
            for (Object value : projectIds) {
                em.createNativeQuery("select id from project where id = :id for update")
                        .setParameter("id", ((Number) value).longValue()).getResultList();
            }
            List<?> rows = em.createNativeQuery("""
                    select p.id, p.status, m.member_type, p.deleted_at from project_member m
                    join project p on p.id = m.project_id where m.profile_id = :id order by p.id
                    """).setParameter("id", profileId).getResultList();
            for (Object item : rows) {
                Object[] row = (Object[]) item;
                if (row[3] == null && "IN_PROGRESS".equals(row[1]) && "LEADER".equals(row[2])) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "진행 중인 프로젝트의 팀장 권한을 위임해야 탈퇴할 수 있습니다.");
                }
            }
            for (Object item : rows) {
                Object[] row = (Object[]) item;
                if (row[3] == null && "PREPARING".equals(row[1]) && "LEADER".equals(row[2])) {
                    Long projectId = ((Number) row[0]).longValue();
                    var project = projects.findById(projectId).orElseThrow();
                    notifications.sendToOtherMembers(project, accountId, NotificationType.PROJECT_DELETED);
                    members.deleteAllByProject_Id(projectId);
                    projects.delete(project);
                }
            }
            em.flush();
            em.createNativeQuery("delete from participation_request where sender_profile_id = :id or participant_profile_id = :id")
                    .setParameter("id", profileId).executeUpdate();
            em.createNativeQuery("delete from profile_bookmark where profile_id = :id")
                    .setParameter("id", profileId).executeUpdate();
            em.createNativeQuery("""
                    update project_member set profile_id = null where profile_id = :id
                    and project_id in (select id from project where status = 'COMPLETED')
                    """).setParameter("id", profileId).executeUpdate();
            em.createNativeQuery("delete from project_member where profile_id = :id")
                    .setParameter("id", profileId).executeUpdate();
            profileService.deleteProfileDetails(profileId);
            em.flush();
            // Bulk cleanup bypasses the persistence context; discard stale associations before removal.
            em.clear();
            em.remove(em.find(com.gopick.server.profile.domain.Profile.class, profileId));
        }
        em.createNativeQuery("delete from profile_bookmark where account_id = :id").setParameter("id", accountId).executeUpdate();
        em.createNativeQuery("delete from project_scrap where account_id = :id").setParameter("id", accountId).executeUpdate();
        em.flush();
        em.remove(em.find(Account.class, accountId));
    }
}
