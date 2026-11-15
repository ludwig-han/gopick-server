package com.gopick.server.notification;

import com.gopick.server.account.domain.Account;
import com.gopick.server.project.domain.Project;
import com.gopick.server.project.repository.ProjectMembershipRepository;
import com.gopick.server.project.service.ProjectAccess;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final ProjectMembershipRepository memberships;
    private final ProjectAccess access;

    public NotificationService(NotificationRepository notifications, ProjectMembershipRepository memberships, ProjectAccess access) {
        this.notifications = notifications;
        this.memberships = memberships;
        this.access = access;
    }

    // The caller's transaction includes the event and its notifications.
    public void send(Account recipient, NotificationType type, Project project, Long requestId) {
        notifications.save(new Notification(recipient, type, project.getId(), project.getTitle(), requestId));
    }

    public void sendToOtherMembers(Project project, Long actorId, NotificationType type) {
        memberships.findMembers(project.getId()).stream()
                .map(member -> member.getProfile().getAccount())
                .filter(account -> !account.getId().equals(actorId))
                .forEach(account -> send(account, type, project, null));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(Long accountId) {
        access.requireAccount(accountId);
        return notifications.findAllByRecipient_IdOrderByIdDesc(accountId).stream().map(Notification::response).toList();
    }

    @Transactional
    public void markRead(Long accountId, Long id) {
        access.requireAccount(accountId);
        notifications.findByIdAndRecipient_Id(id, accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)).markRead();
    }
}
