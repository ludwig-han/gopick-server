package com.gopick.server.global;

import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.time.Duration;

public final class DatabaseDeadlockRetry {
    private static final int MYSQL_DEADLOCK = 1213;
    private static final String SERIALIZATION_FAILURE = "40001";
    private static final RetryTemplate RETRY = new RetryTemplate(RetryPolicy.builder()
            .maxRetries(2)
            .delay(Duration.ofMillis(25))
            .jitter(Duration.ofMillis(10))
            .predicate(DatabaseDeadlockRetry::isRetryableRollback)
            .build());

    private DatabaseDeadlockRetry() {
    }

    // The action calls a transactional service proxy. Every attempt must use a new transaction.
    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        RETRY.invoke(action);
    }

    private static boolean isRetryableRollback(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql &&
                    (sql.getErrorCode() == MYSQL_DEADLOCK || SERIALIZATION_FAILURE.equals(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }
}
