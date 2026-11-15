package com.gopick.server.notification;

import com.gopick.server.account.domain.Account;
import com.gopick.server.project.domain.Project;
import com.gopick.server.project.repository.ProjectMembershipRepository;
import com.gopick.server.project.service.ProjectAccess;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                .filter(member -> member.getProfile() != null)
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
    public void markRead(Long accountId, String rawId) {
        access.requireAccount(accountId);
        long id = parseNotificationId(rawId);
        notifications.findByIdAndRecipient_Id(id, accountId)
                .orElseThrow(NotificationNotFoundException::new).markRead();
    }

    private static long parseNotificationId(String rawId) {
        try {
            long id = Long.parseLong(rawId);
            if (id > 0 && rawId.matches("[0-9]+")) {
                return id;
            }
        } catch (NumberFormatException ignored) {
            // Invalid and out-of-range IDs use the same validation response.
        }
        throw new InvalidNotificationIdException();
    }
}
