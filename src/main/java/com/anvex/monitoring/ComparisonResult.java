package com.anvex.monitoring;

/**
 * Immutable before/after result for one defense-disabled and one
 * defense-enabled experiment.
 */
public record ComparisonResult(
        long beforeRunId,
        long afterRunId,
        MetricsCollector.MetricsSnapshot before,
        MetricsCollector.MetricsSnapshot after
) {
    public ComparisonResult {
        if (beforeRunId <= 0 || afterRunId <= 0) {
            throw new IllegalArgumentException("Run IDs must be positive");
        }
        if (before == null || after == null) {
            throw new IllegalArgumentException("Metrics cannot be null");
        }
    }

    public boolean beforeCompromised() {
        return before.accountCompromised();
    }

    public boolean afterProtected() {
        return !after.accountCompromised();
    }

    public long attemptDelta() {
        return before.totalAttackerAttempts()
                - after.totalAttackerAttempts();
    }

    public long blockedDelta() {
        return after.attackerBlocked()
                - before.attackerBlocked();
    }
}
