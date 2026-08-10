package com.anvex.defense;

import java.util.function.Supplier;

/** Strategy interface for authentication defenses. */
public interface DefenseStrategy {
    String getName();
    boolean isBlocked(String username);
    AuthenticationDecision authenticate(String username, Supplier<Boolean> credentialVerifier);
    void recordFailure(String username);
    void reset();
    int getFailureCount(String username);
    boolean isLocked(String username);
}
