package com.anvex.detection;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable security alert produced by a detection rule. */
public record Alert(
        String alertId,
        long runId,
        String ruleId,
        String severity,
        Instant timestamp,
        String username,
        String evidence,
        String explanation,
        String suggestedDefense
) {
    public Alert {
        alertId = Objects.requireNonNullElseGet(alertId, () -> UUID.randomUUID().toString());
        ruleId = Objects.requireNonNull(ruleId, "ruleId");
        severity = Objects.requireNonNull(severity, "severity");
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        username = Objects.requireNonNull(username, "username");
        evidence = Objects.requireNonNullElse(evidence, "");
        explanation = Objects.requireNonNull(explanation, "explanation");
        suggestedDefense = Objects.requireNonNullElse(suggestedDefense, "");
    }
}
