package com.anvex.defense.strategies;

import com.anvex.defense.AuthenticationDecision;
import com.anvex.defense.DefenseStrategy;
import com.anvex.util.AppConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Locks an account after the configured number of failed authentications. */
public final class AccountLockoutDefense implements DefenseStrategy {
    private final Map<String, AccountState> states = new ConcurrentHashMap<>();
    private final int threshold;

    public AccountLockoutDefense() {
        this(AppConfig.LOCKOUT_THRESHOLD);
    }

    public AccountLockoutDefense(int threshold) {
        if (threshold <= 0) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        this.threshold = threshold;
    }

    @Override
    public String getName() {
        return "Account Lockout Defense";
    }

    @Override
    public boolean isBlocked(String username) {
        return state(username).locked;
    }

    @Override
    public AuthenticationDecision authenticate(String username, Supplier<Boolean> credentialVerifier) {
        AccountState state = state(username);
        synchronized (state) {
            if (state.locked) {
                return AuthenticationDecision.BLOCKED;
            }

            // Credential verification happens inside the same per-account critical section
            // as the failure counter, preventing a correct-password request from racing
            // past the lockout boundary.
            boolean valid = credentialVerifier.get();
            if (valid) {
                return AuthenticationDecision.SUCCESS;
            }

            state.failures++;
            if (state.failures >= threshold) {
                state.locked = true;
            }
            return AuthenticationDecision.FAILURE;
        }
    }

    @Override
    public void recordFailure(String username) {
        // Failure accounting is performed atomically by authenticate().
        // This method exists for the Strategy contract and external event-driven use.
        AccountState state = state(username);
        synchronized (state) {
            if (!state.locked) {
                state.failures++;
                if (state.failures >= threshold) {
                    state.locked = true;
                }
            }
        }
    }

    @Override
    public void reset() {
        states.clear();
    }

    @Override
    public int getFailureCount(String username) {
        return state(username).failures;
    }

    @Override
    public boolean isLocked(String username) {
        return state(username).locked;
    }

    public int getThreshold() {
        return threshold;
    }

    private AccountState state(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be blank");
        }
        return states.computeIfAbsent(username, ignored -> new AccountState());
    }

    private static final class AccountState {
        private int failures;
        private boolean locked;
    }
}
