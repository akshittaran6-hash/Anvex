package com.anvex.event;

public enum ThreatLevel {
    NORMAL(0),
    SUSPICIOUS(1),
    HIGH(2),
    CRITICAL(3);

    private final int severity;

    ThreatLevel(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }

    public boolean isAtLeast(ThreatLevel other) {
        return this.severity >= other.severity;
    }
}
