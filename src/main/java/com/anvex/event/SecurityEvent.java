package com.anvex.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class SecurityEvent {

    private final long sequenceNumber;
    private final String eventId;
    private final long runId;
    private final Instant timestamp;
    private final SecurityEventType eventType;
    private final String username;
    private final String clientType;
    private final String source;
    private final String outcome;
    private final String message;
    private final Map<String, String> metadata;
    private final ThreatLevel threatLevel;
    private final Integer threatScore;
    private final Evidence evidence;

    private SecurityEvent(Builder builder) {
        this.sequenceNumber = builder.sequenceNumber;
        this.eventId = builder.eventId;
        this.runId = builder.runId;
        this.timestamp = builder.timestamp;
        this.eventType = builder.eventType;
        this.username = builder.username;
        this.clientType = builder.clientType;
        this.source = builder.source;
        this.outcome = builder.outcome;
        this.message = builder.message;
        this.metadata = builder.metadata == null ? Map.of() : Map.copyOf(builder.metadata);
        this.threatLevel = builder.threatLevel;
        this.threatScore = builder.threatScore;
        this.evidence = builder.evidence;
    }

    public long getSequenceNumber() { return sequenceNumber; }
    public String getEventId() { return eventId; }
    public long getRunId() { return runId; }
    public Instant getTimestamp() { return timestamp; }
    public SecurityEventType getEventType() { return eventType; }
    public String getUsername() { return username; }
    public String getClientType() { return clientType; }
    public String getSource() { return source; }
    public String getOutcome() { return outcome; }
    public String getMessage() { return message; }
    public Map<String, String> getMetadata() { return metadata; }
    public ThreatLevel getThreatLevel() { return threatLevel; }
    public Integer getThreatScore() { return threatScore; }
    public Evidence getEvidence() { return evidence; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long sequenceNumber;
        private String eventId = UUID.randomUUID().toString();
        private long runId;
        private Instant timestamp = Instant.now();
        private SecurityEventType eventType;
        private String username;
        private String clientType;
        private String source;
        private String outcome;
        private String message;
        private Map<String, String> metadata;
        private ThreatLevel threatLevel;
        private Integer threatScore;
        private Evidence evidence;

        public Builder sequenceNumber(long sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return this;
        }
        public Builder eventId(String eventId) {
            this.eventId = eventId;
            return this;
        }
        public Builder runId(long runId) {
            this.runId = runId;
            return this;
        }
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        public Builder eventType(SecurityEventType eventType) {
            this.eventType = eventType;
            return this;
        }
        public Builder username(String username) {
            this.username = username;
            return this;
        }
        public Builder clientType(String clientType) {
            this.clientType = clientType;
            return this;
        }
        public Builder source(String source) {
            this.source = source;
            return this;
        }
        public Builder outcome(String outcome) {
            this.outcome = outcome;
            return this;
        }
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        public Builder metadata(Map<String, String> metadata) {
            this.metadata = metadata;
            return this;
        }
        public Builder threatLevel(ThreatLevel threatLevel) {
            this.threatLevel = threatLevel;
            return this;
        }

        public Builder threatScore(Integer threatScore) {
            this.threatScore = threatScore;
            return this;
        }
        public Builder evidence(Evidence evidence) {
            this.evidence = evidence;
            return this;
        }
        public SecurityEvent build() {
            return new SecurityEvent(this);
        }
    }

    @Override
    public String toString() {
        String threatInfo = threatLevel != null ? " | threat=" + threatLevel : "";
        return String.format("[%d] %s | %s | user=%s | type=%s | outcome=%s%s | %s",
                sequenceNumber, timestamp, eventType, username, clientType, outcome, threatInfo, message);
    }
}
