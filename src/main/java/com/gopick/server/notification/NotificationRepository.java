package com.gopick.server.notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
interface NotificationRepository extends JpaRepository<Notification, Long> {
 List<Notification> findAllByRecipient_IdOrderByIdDesc(Long accountId);
 Optional<Notification> findByIdAndRecipient_Id(Long id, Long accountId);
}
