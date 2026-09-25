package com.anvex.protection;

import com.anvex.detection.DetectionEngine;
import com.anvex.detection.ThreatAssessment;
import com.anvex.event.Evidence;
import com.anvex.event.EventBus;
import com.anvex.event.EventListener;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.event.ThreatLevel;
import com.anvex.server.RunManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class ProtectionEngine implements EventListener {

    private static final Logger logger = LoggerFactory.getLogger(ProtectionEngine.class);

    private final DetectionEngine detectionEngine;
    private final ProtectionConfig config;
    private final EventBus eventBus;
    private final RunManager runManager;
    private final ConcurrentMap<String, SourceProtection> sources = new ConcurrentHashMap<>();
    private ScheduledExecutorService reaper;

    public ProtectionEngine(DetectionEngine detectionEngine, ProtectionConfig config, EventBus eventBus) {
        this(detectionEngine, config, eventBus, null);
    }

    public ProtectionEngine(DetectionEngine detectionEngine, ProtectionConfig config,
                            EventBus eventBus, RunManager runManager) {
        this.detectionEngine = detectionEngine;
        this.config = config;
        this.eventBus = eventBus;
        this.runManager = runManager;
    }

    public void start() {
        if (reaper != null) return;
        reaper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ProtectionEngine-Reaper");
            t.setDaemon(true);
            return t;
        });
        reaper.scheduleWithFixedDelay(this::reapExpired,
                config.getReaperIntervalMs(), config.getReaperIntervalMs(), TimeUnit.MILLISECONDS);
        logger.info("ProtectionEngine started (reaper interval {}ms, block cooldown {}ms, high delay {}ms)",
                config.getReaperIntervalMs(), config.getBlockCooldownMs(), config.getHighDelayMs());
    }

    public void stop() {
        if (reaper != null) {
            reaper.shutdownNow();
            reaper = null;
        }
    }

    public synchronized void reschedule() {
        if (reaper != null) { stop(); start(); }
    }

    @Override
    public void onEvent(SecurityEvent event) {
        switch (event.getEventType()) {
            case ANOMALY_DETECTED -> enterSuspiciousActivity(event.getSource());
            case THREAT_ESCALATED -> handleEscalation(event);
            case THREAT_DEESCALATED -> handleDeescalation(event);
            default -> { }
        }
    }

    public ProtectionAction evaluateRequest(String sourceId, Instant now) {
        SourceProtection protection = sources.get(sourceId);
        if (protection == null) {
            return ProtectionAction.ALLOW;
        }

        synchronized (protection) {
            if (protection.phase == ProtectionPhase.BLOCKED) {
                if (now.isBefore(protection.blockedUntil)) {
                    return ProtectionAction.BLOCK;
                }
                releaseBlocked(sourceId, protection, now);
            }
            if (protection.phase == ProtectionPhase.PROTECTION_ENABLED
                    || protection.phase == ProtectionPhase.SUSPICIOUS_ACTIVITY) {
                ProtectionAction recheck = recheckThreatLevel(sourceId, protection, now);
                if (recheck != null) {
                    return recheck;
                }
                if (protection.phase == ProtectionPhase.PROTECTION_ENABLED) {
                    return ProtectionAction.DELAY;
                }
                return ProtectionAction.MONITOR;
            }
            return ProtectionAction.ALLOW;
        }
    }

    private ProtectionAction recheckThreatLevel(String sourceId, SourceProtection protection, Instant now) {
        ThreatAssessment assessment = detectionEngine.assess(sourceId, now);
        if (assessment.getThreatLevel() == ThreatLevel.CRITICAL && protection.phase != ProtectionPhase.BLOCKED) {
            protection.phase = ProtectionPhase.BLOCKED;
            protection.blockedUntil = now.plusMillis(config.getBlockCooldownMs());
            publishBlockFromAssessment(sourceId, assessment, protection.runId);
            return ProtectionAction.BLOCK;
        }
        return null;
    }

    private void publishBlockFromAssessment(String sourceId, ThreatAssessment assessment, long runId) {
        eventBus.publish(SecurityEvent.builder()
                .runId(runId)
                .eventType(SecurityEventType.SOURCE_BLOCKED)
                .source(sourceId)
                .outcome(ThreatLevel.CRITICAL.name())
                .message(String.format("Source %s re-blocked: threat remains CRITICAL after release", sourceId))
                .threatLevel(ThreatLevel.CRITICAL)
                .threatScore(assessment.getThreatScore())
                .metadata(Map.of("reason", assessment.getReason()))
                .evidence(assessment.getEvidence())
                .build());
        publishIncidentEvent(sourceId, runId, ThreatLevel.CRITICAL, assessment.getEvidence(),
                String.format("Security incident opened: source %s re-blocked under CRITICAL threat", sourceId));
        logger.info("SOURCE_BLOCKED: source {} re-blocked (threat still CRITICAL after release)", sourceId);
    }

    private long currentRunId() {
        return runManager != null ? runManager.getCurrentRunId() : 0;
    }

    public ProtectionPhase getPhase(String sourceId) {
        SourceProtection protection = sources.get(sourceId);
        return protection != null ? protection.phase : ProtectionPhase.READY;
    }

    public Instant getBlockedUntil(String sourceId) {
        SourceProtection p = sources.get(sourceId);
        return p == null ? null : p.blockedUntil;
    }

    private void handleDeescalation(SecurityEvent event) {
        SourceProtection p = sources.get(event.getSource());
        if (p == null || event.getThreatLevel() == null) return;
        synchronized (p) {
            if (p.phase == ProtectionPhase.BLOCKED) return;
            p.phase = switch (event.getThreatLevel()) {
                case NORMAL -> ProtectionPhase.READY;
                case SUSPICIOUS -> ProtectionPhase.SUSPICIOUS_ACTIVITY;
                case HIGH -> ProtectionPhase.PROTECTION_ENABLED;
                case CRITICAL -> p.phase;
            };
        }
    }

    public long getHighDelayMs() {
        return config.getHighDelayMs();
    }

    public void reset() {
        sources.clear();
    }

    private void handleEscalation(SecurityEvent event) {
        String sourceId = event.getSource();
        ThreatLevel level = event.getThreatLevel();
        if (sourceId == null || level == null) return;

        if (level == ThreatLevel.CRITICAL) {
            applyBlock(sourceId, event);
        } else if (level == ThreatLevel.HIGH) {
            enableProtection(sourceId, event);
        }
    }

    private void enterSuspiciousActivity(String sourceId) {
        if (sourceId == null) return;
        SourceProtection protection = sources.computeIfAbsent(sourceId, k -> new SourceProtection());
        synchronized (protection) {
            if (protection.phase == ProtectionPhase.READY) {
                protection.phase = ProtectionPhase.SUSPICIOUS_ACTIVITY;
                logger.info("Source {} entered SUSPICIOUS_ACTIVITY (monitoring)", sourceId);
            }
        }
    }

    private void enableProtection(String sourceId, SecurityEvent trigger) {
        SourceProtection protection = sources.computeIfAbsent(sourceId, k -> new SourceProtection());
        boolean transitioned = false;
        synchronized (protection) {
            if (protection.phase != ProtectionPhase.PROTECTION_ENABLED
                    && protection.phase != ProtectionPhase.BLOCKED) {
                protection.phase = ProtectionPhase.PROTECTION_ENABLED;
                transitioned = true;
            }
        }
        if (transitioned) {
            publishProtectionEvent(SecurityEventType.PROTECTION_ENABLED, trigger, sourceId,
                    String.format("Protection enabled for %s: responses delayed by %dms (tarpitting)",
                            sourceId, config.getHighDelayMs()));
        }
    }

    private void applyBlock(String sourceId, SecurityEvent trigger) {
        Instant now = trigger.getTimestamp();
        SourceProtection protection = sources.computeIfAbsent(sourceId, k -> new SourceProtection());
        boolean transitioned = false;
        synchronized (protection) {
            if (protection.phase != ProtectionPhase.BLOCKED) {
                protection.phase = ProtectionPhase.BLOCKED;
                protection.blockedUntil = now.plusMillis(config.getBlockCooldownMs());
                protection.runId = trigger.getRunId();
                transitioned = true;
            }
        }
        if (transitioned) {
            publishProtectionEvent(SecurityEventType.SOURCE_BLOCKED, trigger, sourceId,
                    String.format("Source %s temporarily blocked for %d seconds",
                            sourceId, config.getBlockCooldownMs() / 1000));
            publishIncidentEvent(sourceId, trigger.getRunId(), trigger.getThreatLevel(), trigger.getEvidence(),
                    String.format("Source %s blocked under CRITICAL threat", sourceId));
        }
    }

    private void releaseBlocked(String sourceId, SourceProtection protection, Instant now) {
        ThreatLevel level = detectionEngine.assess(sourceId, now).getThreatLevel();
        protection.phase = switch (level) {
            case NORMAL -> ProtectionPhase.READY;
            case SUSPICIOUS, CRITICAL -> ProtectionPhase.SUSPICIOUS_ACTIVITY;
            case HIGH -> ProtectionPhase.PROTECTION_ENABLED;
        };
        protection.blockedUntil = null;
        publishReleaseEvent(sourceId, now, protection.runId);
    }

    private void reapExpired() {
        Instant now = Instant.now();
        for (Map.Entry<String, SourceProtection> entry : sources.entrySet()) {
            detectionEngine.assess(entry.getKey(), now);
            SourceProtection protection = entry.getValue();
            synchronized (protection) {
                if (protection.phase == ProtectionPhase.BLOCKED && now.isAfter(protection.blockedUntil)) {
                    releaseBlocked(entry.getKey(), protection, now);
                }
            }
        }
        releaseDecayedSources(now);
    }

    private void releaseDecayedSources(Instant now) {
        for (Map.Entry<String, SourceProtection> entry : sources.entrySet()) {
            String sourceId = entry.getKey();
            SourceProtection protection = entry.getValue();
            synchronized (protection) {
                if (protection.phase == ProtectionPhase.READY || protection.phase == ProtectionPhase.BLOCKED) {
                    continue;
                }
            }
            ThreatAssessment assessment = detectionEngine.assess(sourceId, now);
            if (assessment.getThreatLevel() != ThreatLevel.CRITICAL) {
                synchronized (protection) {
                    if (protection.phase != ProtectionPhase.BLOCKED) {
                        protection.phase = switch (assessment.getThreatLevel()) {
                            case NORMAL -> ProtectionPhase.READY;
                            case SUSPICIOUS -> ProtectionPhase.SUSPICIOUS_ACTIVITY;
                            case HIGH -> ProtectionPhase.PROTECTION_ENABLED;
                            case CRITICAL -> protection.phase;
                        };
                    }
                }
            }
        }
    }

    private void publishProtectionEvent(SecurityEventType eventType, SecurityEvent trigger,
                                        String sourceId, String message) {
        eventBus.publish(SecurityEvent.builder()
                .runId(trigger.getRunId())
                .eventType(eventType)
                .source(sourceId)
                .outcome(eventType.name())
                .message(message)
                .threatLevel(trigger.getThreatLevel())
                .threatScore(trigger.getThreatScore())
                .metadata(trigger.getMetadata())
                .evidence(trigger.getEvidence())
                .build());
        logger.info("{}: {}", eventType, message);
    }

    private void publishReleaseEvent(String sourceId, Instant now, long runId) {
        eventBus.publish(SecurityEvent.builder()
                .timestamp(now)
                .runId(runId)
                .eventType(SecurityEventType.SOURCE_RELEASED)
                .source(sourceId)
                .outcome("RELEASED")
                .message(String.format("Block on %s expired; current threat state re-evaluated", sourceId))
                .build());
        logger.info("SOURCE_RELEASED: block on {} expired", sourceId);
    }

    private void publishIncidentEvent(String sourceId, long runId, ThreatLevel level,
                                      Evidence evidence, String reason) {
        String incidentId = "INC-" + UUID.randomUUID().toString().substring(0, 8);
        eventBus.publish(SecurityEvent.builder()
                .runId(runId)
                .eventType(SecurityEventType.SECURITY_INCIDENT)
                .source(sourceId)
                .outcome("INCIDENT_OPENED")
                .message(String.format("Security incident %s opened: %s", incidentId, reason))
                .threatLevel(level)
                .threatScore(detectionEngine.getStoredScore(sourceId))
                .evidence(evidence)
                .metadata(Map.of("incidentId", incidentId))
                .build());
        logger.info("SECURITY_INCIDENT {} opened for source {}", incidentId, sourceId);
    }

    private static final class SourceProtection {
        private volatile ProtectionPhase phase = ProtectionPhase.READY;
        private volatile Instant blockedUntil;
        private long runId;
    }
}
