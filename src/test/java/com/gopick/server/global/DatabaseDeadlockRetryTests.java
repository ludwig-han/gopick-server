package com.gopick.server.global;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseDeadlockRetryTests {
    @Test
    void lockWaitTimeoutIsNotRetried() {
        var attempts = new AtomicInteger();
        var failure = new CannotAcquireLockException("Lock wait timeout",
                new SQLException("Lock wait timeout", "HY000", 1205));

        assertThatThrownBy(() -> DatabaseDeadlockRetry.run(() -> {
            attempts.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void repeatedDeadlocksHaveABoundedRetry() {
        var attempts = new AtomicInteger();
        var failure = new CannotAcquireLockException("Deadlock", new SQLException("Deadlock", "40001", 1213));

        assertThatThrownBy(() -> DatabaseDeadlockRetry.run(() -> {
            attempts.incrementAndGet();
            throw failure;
        })).isSameAs(failure);

        assertThat(attempts.get()).isEqualTo(3);
    }
}
