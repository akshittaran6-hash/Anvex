package com.anvex.detection.rules;

import com.anvex.detection.Alert;
import com.anvex.detection.DetectionRule;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Detects five failed logins for the same account within a run. */
public final class RepeatedFailedLoginRule implements DetectionRule {
    public static final String RULE_ID = "REPEATED_FAILED_LOGIN";
    private static final int THRESHOLD = 5;

    private final Map<Long, Map<String, AtomicInteger>> failuresByRun = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Boolean>> alertedByRun = new ConcurrentHashMap<>();

    @Override
    public String getRuleId() {
        return RULE_ID;
    }

    @Override
    public Optional<Alert> evaluate(SecurityEvent event) {
        if (event == null || event.getEventType() != SecurityEventType.LOGIN_FAILURE
                || event.getUsername() == null) {
            return Optional.empty();
        }

        long runId = event.getRunId();
        String username = event.getUsername();
        Map<String, AtomicInteger> accountFailures = failuresByRun.computeIfAbsent(
                runId, ignored -> new ConcurrentHashMap<>());
        Map<String, Boolean> accountAlerts = alertedByRun.computeIfAbsent(
                runId, ignored -> new ConcurrentHashMap<>());

        int count = accountFailures.computeIfAbsent(username, ignored -> new AtomicInteger())
                .incrementAndGet();

        if (count == THRESHOLD && accountAlerts.putIfAbsent(username, Boolean.TRUE) == null) {
            return Optional.of(new Alert(
                    null,
                    runId,
                    RULE_ID,
                    "HIGH",
                    event.getTimestamp() == null ? Instant.now() : event.getTimestamp(),
                    username,
                    "Failed login count reached " + THRESHOLD,
                    "Five failed login attempts were detected for " + username
                            + ". This matches the repeated failed-login rule and may indicate an automated password attack.",
                    "Enable account lockout after repeated failed authentication attempts."
            ));
        }

        return Optional.empty();
    }

    @Override
    public void reset() {
        failuresByRun.clear();
        alertedByRun.clear();
    }
}
