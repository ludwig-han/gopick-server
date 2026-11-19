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

    @Test
    void propagatesNonDeadlockFailuresWithoutRetrying() {
        var attempts = new AtomicInteger();
        assertThatThrownBy(() -> DatabaseDeadlockRetry.call(() -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("invalid input");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    void persistentDeadlockStopsAfterThreeAttempts() {
        var attempts = new AtomicInteger();
        assertThatThrownBy(() -> DatabaseDeadlockRetry.call(() -> {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("deadlock", new SQLException("deadlock", "40001", 1213));
        })).isInstanceOf(CannotAcquireLockException.class);
        assertThat(attempts.get()).isEqualTo(3);
    }
}
