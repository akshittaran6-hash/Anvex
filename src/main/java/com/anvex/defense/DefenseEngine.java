package com.anvex.defense;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/** Coordinates the currently active authentication defense strategies. */
public final class DefenseEngine {
    private final List<DefenseStrategy> strategies = new CopyOnWriteArrayList<>();

    public void addStrategy(DefenseStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy cannot be null");
        }
        strategies.add(strategy);
    }

    public void clearStrategies() {
        strategies.clear();
    }

    public boolean isEmpty() {
        return strategies.isEmpty();
    }

    public boolean isBlocked(String username) {
        for (DefenseStrategy strategy : strategies) {
            if (strategy.isBlocked(username)) {
                return true;
            }
        }
        return false;
    }

    public AuthenticationDecision authenticate(String username, Supplier<Boolean> credentialVerifier) {
        if (credentialVerifier == null) {
            throw new IllegalArgumentException("credentialVerifier cannot be null");
        }

        for (DefenseStrategy strategy : strategies) {
            AuthenticationDecision decision = strategy.authenticate(username, credentialVerifier);
            if (decision == AuthenticationDecision.BLOCKED || decision == AuthenticationDecision.FAILURE) {
                return decision;
            }
            if (decision == AuthenticationDecision.SUCCESS) {
                return decision;
            }
        }
        return credentialVerifier.get() ? AuthenticationDecision.SUCCESS : AuthenticationDecision.FAILURE;
    }

    public void recordFailure(String username) {
        for (DefenseStrategy strategy : strategies) {
            strategy.recordFailure(username);
        }
    }

    public void reset() {
        for (DefenseStrategy strategy : strategies) {
            strategy.reset();
        }
    }
}
