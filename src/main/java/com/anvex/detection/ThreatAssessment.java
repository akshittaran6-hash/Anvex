package com.anvex.detection;

import com.anvex.event.Evidence;
import com.anvex.event.ThreatLevel;

import java.time.Instant;

public final class ThreatAssessment {

    private final String sourceId;
    private final ThreatLevel threatLevel;
    private final int threatScore;
    private final Evidence evidence;
    private final Instant timestamp;
    private final boolean escalated;
    private final String reason;

    public ThreatAssessment(String sourceId, ThreatLevel threatLevel, int threatScore,
                            Evidence evidence, Instant timestamp, boolean escalated, String reason) {
        this.sourceId = sourceId;
        this.threatLevel = threatLevel;
        this.threatScore = threatScore;
        this.evidence = evidence;
        this.timestamp = timestamp;
        this.escalated = escalated;
        this.reason = reason;
    }

    public String getSourceId() { return sourceId; }
    public ThreatLevel getThreatLevel() { return threatLevel; }
    public int getThreatScore() { return threatScore; }
    public Evidence getEvidence() { return evidence; }
    public Instant getTimestamp() { return timestamp; }
    public boolean isEscalated() { return escalated; }
    public String getReason() { return reason; }

    @Override
    public String toString() {
        return String.format("ThreatAssessment{source=%s, level=%s, score=%d, escalated=%s}",
                sourceId, threatLevel, threatScore, escalated);
    }
}
