package com.anvex;

import com.anvex.defense.AuthenticationDecision;
import com.anvex.defense.strategies.AccountLockoutDefense;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AccountLockoutDefenseTest {

    @Test
    void locksExactlyAtThreshold() {
        AccountLockoutDefense defense = new AccountLockoutDefense(5);
        AtomicInteger verifications = new AtomicInteger();

        for (int i = 1; i <= 5; i++) {
            AuthenticationDecision decision = defense.authenticate(
                    "lab_target",
                    () -> {
                        verifications.incrementAndGet();
                        return false;
                    });

            assertEquals(AuthenticationDecision.FAILURE, decision);
            assertEquals(i, defense.getFailureCount("lab_target"));
            assertEquals(i == 5, defense.isLocked("lab_target"));
        }

        assertEquals(5, verifications.get());
    }

    @Test
    void lockedAccountDoesNotVerifyCredentials() {
        AccountLockoutDefense defense = new AccountLockoutDefense(5);
        AtomicInteger verifications = new AtomicInteger();

        for (int i = 0; i < 5; i++) {
            assertEquals(AuthenticationDecision.FAILURE, defense.authenticate(
                    "lab_target", () -> {
                        verifications.incrementAndGet();
                        return false;
                    }));
        }

        assertEquals(AuthenticationDecision.BLOCKED, defense.authenticate(
                "lab_target", () -> {
                    verifications.incrementAndGet();
                    return true;
                }));

        assertEquals(5, verifications.get(),
                "Credential verification must not run after lockout");
    }

    @Test
    void resetClearsFailureAndLockState() {
        AccountLockoutDefense defense = new AccountLockoutDefense(5);

        for (int i = 0; i < 5; i++) {
            defense.authenticate("lab_target", () -> false);
        }
        assertTrue(defense.isLocked("lab_target"));

        defense.reset();

        assertFalse(defense.isLocked("lab_target"));
        assertEquals(0, defense.getFailureCount("lab_target"));
        assertEquals(AuthenticationDecision.SUCCESS,
                defense.authenticate("lab_target", () -> true));
    }

    @Test
    void concurrentFailuresStillProduceExactlyOneLockBoundary() throws InterruptedException {
        AccountLockoutDefense defense = new AccountLockoutDefense(5);
        int requests = 32;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch finished = new CountDownLatch(requests);
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < requests; i++) {
            pool.submit(() -> {
                try {
                    AuthenticationDecision decision = defense.authenticate("lab_target", () -> false);
                    switch (decision) {
                        case FAILURE -> failures.incrementAndGet();
                        case BLOCKED -> blocked.incrementAndGet();
                        case SUCCESS -> success.incrementAndGet();
                    }
                } finally {
                    finished.countDown();
                }
            });
        }

        assertTrue(finished.await(5, TimeUnit.SECONDS));
        pool.shutdown();

        assertEquals(5, failures.get());
        assertEquals(requests - 5, blocked.get());
        assertEquals(0, success.get());
        assertEquals(5, defense.getFailureCount("lab_target"));
        assertTrue(defense.isLocked("lab_target"));
    }
}
