package com.anvex.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Evidence {

    private final long failedAttemptCount;
    private final long timeWindowMs;
    private final double requestFrequency;
    private final boolean thresholdExceeded;

    private Evidence(Builder builder) {
        this.failedAttemptCount = builder.failedAttemptCount;
        this.timeWindowMs = builder.timeWindowMs;
        this.requestFrequency = builder.requestFrequency;
        this.thresholdExceeded = builder.thresholdExceeded;
    }

    public long getFailedAttemptCount() { return failedAttemptCount; }
    public long getTimeWindowMs() { return timeWindowMs; }
    public double getRequestFrequency() { return requestFrequency; }
    public boolean isThresholdExceeded() { return thresholdExceeded; }

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("%d failed login attempts detected", failedAttemptCount));
        if (timeWindowMs > 0) {
            double seconds = timeWindowMs / 1000.0;
            if (requestFrequency > 0) {
                lines.add(String.format("Occurred within %.1f seconds (%.1f attempts/second)", seconds, requestFrequency));
            } else {
                lines.add(String.format("Occurred within %.1f seconds", seconds));
            }
        }
        if (thresholdExceeded) {
            lines.add("Threat threshold exceeded");
        }
        return Collections.unmodifiableList(lines);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long failedAttemptCount;
        private long timeWindowMs;
        private double requestFrequency;
        private boolean thresholdExceeded;

        public Builder failedAttemptCount(long failedAttemptCount) {
            this.failedAttemptCount = failedAttemptCount;
            return this;
        }
        public Builder timeWindowMs(long timeWindowMs) {
            this.timeWindowMs = timeWindowMs;
            return this;
        }
        public Builder requestFrequency(double requestFrequency) {
            this.requestFrequency = requestFrequency;
            return this;
        }
        public Builder thresholdExceeded(boolean thresholdExceeded) {
            this.thresholdExceeded = thresholdExceeded;
            return this;
        }
        public Evidence build() {
            return new Evidence(this);
        }
    }

    @Override
    public String toString() {
        return String.format("Evidence{failedAttempts=%d, timeWindowMs=%d, frequency=%.2f, thresholdExceeded=%s}",
                failedAttemptCount, timeWindowMs, requestFrequency, thresholdExceeded);
    }
}
