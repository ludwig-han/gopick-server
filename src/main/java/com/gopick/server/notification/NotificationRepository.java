package com.gopick.server.notification;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findAllByRecipient_IdOrderByIdDesc(Long accountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findByIdAndRecipient_Id(Long id, Long accountId);
}
