package com.gopick.server.project.service;

import com.gopick.server.account.domain.Account;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;

@Component
public class ParticipantAccountLock {
    private final EntityManager entityManager;

    public ParticipantAccountLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    // Call before locking the project, as account withdrawal also locks accounts first.
    // Recheck membership/request after the project lock: the account may have disappeared while waiting.
    public void lockIfPresent(Long accountId) {
        if (accountId != null) entityManager.find(Account.class, accountId, LockModeType.PESSIMISTIC_WRITE);
    }
}
