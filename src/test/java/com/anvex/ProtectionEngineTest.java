package com.anvex;

import com.anvex.detection.DetectionConfig;
import com.anvex.detection.DetectionEngine;
import com.anvex.detection.SourceActivityTracker;
import com.anvex.event.Evidence;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.event.ThreatLevel;
import com.anvex.protection.ProtectionConfig;
import com.anvex.protection.ProtectionEngine;
import com.anvex.protection.ProtectionPhase;
import com.anvex.protection.ProtectionAction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class ProtectionEngineTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final String SOURCE = "192.168.1.50";

    private DetectionConfig detectionConfig;
    private DetectionEngine detectionEngine;
    private EventBus eventBus;
    private ProtectionEngine engine;
    private List<SecurityEvent> published;

    @BeforeEach
    void setUp() {
        detectionConfig = new DetectionConfig();
        eventBus = new EventBus();
        detectionEngine = new DetectionEngine(detectionConfig, new SourceActivityTracker(), eventBus);
        engine = new ProtectionEngine(detectionEngine, new ProtectionConfig(), eventBus);
        published = new CopyOnWriteArrayList<>();
        eventBus.subscribe(published::add);
    }

    @AfterEach
    void tearDown() {
        engine.stop();
        eventBus.shutdown();
    }

    private SecurityEvent threatEvent(ThreatLevel level, Instant timestamp) {
        Evidence evidence = Evidence.builder()
                .failedAttemptCount(20)
                .timeWindowMs(30_000)
                .requestFrequency(10.0)
                .thresholdExceeded(true)
                .build();
        return SecurityEvent.builder()
                .runId(1)
                .eventType(SecurityEventType.THREAT_ESCALATED)
                .source(SOURCE)
                .outcome(level.name())
                .message("Threat escalated")
                .threatLevel(level)
                .evidence(evidence)
                .timestamp(timestamp)
                .build();
    }

    private SecurityEvent anomalyEvent(Instant timestamp) {
        Evidence evidence = Evidence.builder()
                .failedAttemptCount(5)
                .timeWindowMs(30_000)
                .requestFrequency(2.5)
                .thresholdExceeded(true)
                .build();
        return SecurityEvent.builder()
                .runId(1)
                .eventType(SecurityEventType.ANOMALY_DETECTED)
                .source(SOURCE)
                .outcome(ThreatLevel.SUSPICIOUS.name())
                .message("Anomalous behaviour detected")
                .threatLevel(ThreatLevel.SUSPICIOUS)
                .evidence(evidence)
                .timestamp(timestamp)
                .build();
    }

    @Test
    void unknownSourceIsAllowed() {
        assertEquals(ProtectionAction.ALLOW, engine.evaluateRequest("unknown-source", BASE));
        assertEquals(ProtectionPhase.READY, engine.getPhase("unknown-source"));
    }

    @Test
    void anomalyEventPutsSourceUnderMonitoring() {
        engine.onEvent(anomalyEvent(BASE));

        assertEquals(ProtectionPhase.SUSPICIOUS_ACTIVITY, engine.getPhase(SOURCE));
        assertEquals(ProtectionAction.MONITOR, engine.evaluateRequest(SOURCE, BASE.plusMillis(100)));
    }

    @Test
    void highEscalationEnablesProtectionWithDelay() throws InterruptedException {
        engine.onEvent(threatEvent(ThreatLevel.HIGH, BASE));

        assertEquals(ProtectionPhase.PROTECTION_ENABLED, engine.getPhase(SOURCE));
        assertEquals(ProtectionAction.DELAY, engine.evaluateRequest(SOURCE, BASE.plusMillis(100)));
        assertTrue(eventBus.awaitIdle(2000), "Events should be processed");

        List<SecurityEvent> protectionEvents = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.PROTECTION_ENABLED)
                .toList();
        assertEquals(1, protectionEvents.size());
        assertNotNull(protectionEvents.get(0).getEvidence(), "Protection event must carry evidence");
        assertTrue(protectionEvents.get(0).getMessage().contains("delayed"));
    }

    @Test
    void criticalEscalationBlocksSource() throws InterruptedException {
        engine.onEvent(threatEvent(ThreatLevel.CRITICAL, BASE));

        assertEquals(ProtectionPhase.BLOCKED, engine.getPhase(SOURCE));
        assertEquals(ProtectionAction.BLOCK, engine.evaluateRequest(SOURCE, BASE.plusMillis(100)));
        assertTrue(eventBus.awaitIdle(2000), "Events should be processed");

        List<SecurityEvent> blockedEvents = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.SOURCE_BLOCKED)
                .toList();
        assertEquals(1, blockedEvents.size());
        assertNotNull(blockedEvents.get(0).getEvidence());
        assertEquals(ThreatLevel.CRITICAL, blockedEvents.get(0).getThreatLevel());
    }

    @Test
    void blockAutoExpiresAfterCooldown() {
        engine.onEvent(threatEvent(ThreatLevel.CRITICAL, BASE));

        assertEquals(ProtectionAction.BLOCK, engine.evaluateRequest(SOURCE, BASE.plusSeconds(30)));
        assertEquals(ProtectionAction.ALLOW, engine.evaluateRequest(SOURCE, BASE.plusSeconds(61)));
        assertEquals(ProtectionPhase.READY, engine.getPhase(SOURCE));
    }

    @Test
    void reaperReleasesExpiredBlocks() throws InterruptedException {
        engine.stop();
        ProtectionConfig fastReaper = new ProtectionConfig();
        fastReaper.setReaperIntervalMs(200);
        fastReaper.setBlockCooldownMs(500);
        engine = new ProtectionEngine(detectionEngine, fastReaper, eventBus);
        eventBus.subscribe(engine);
        engine.start();

        engine.onEvent(threatEvent(ThreatLevel.CRITICAL, Instant.now()));
        assertEquals(ProtectionPhase.BLOCKED, engine.getPhase(SOURCE));

        Thread.sleep(1500);
        assertTrue(eventBus.awaitIdle(2000), "Events should be processed");

        List<SecurityEvent> releases = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.SOURCE_RELEASED)
                .toList();
        assertEquals(1, releases.size(), "Reaper must publish SOURCE_RELEASED on expiry");

        assertNotEquals(ProtectionPhase.BLOCKED, engine.getPhase(SOURCE), "Block must be lifted after cooldown");
        assertEquals(ProtectionPhase.READY, engine.getPhase(SOURCE),
                "With no ongoing threat, the source returns to READY after release");
    }

    @Test
    void reaperReturnsMonitoredSourcesToReadyAfterDecay() throws InterruptedException {
        detectionConfig.setTimeWindowMs(200);
        detectionConfig.setDecayIntervalMs(100);
        detectionConfig.setDecayStepPerInterval(50);

        engine.stop();
        ProtectionConfig fastReaper = new ProtectionConfig();
        fastReaper.setReaperIntervalMs(200);
        engine = new ProtectionEngine(detectionEngine, fastReaper, eventBus);
        eventBus.subscribe(engine);

        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            detectionEngine.onEvent(SecurityEvent.builder()
                    .runId(1)
                    .eventType(SecurityEventType.LOGIN_FAILURE)
                    .username("target_user")
                    .clientType("ATTACKER")
                    .source(SOURCE)
                    .outcome("FAILURE")
                    .message("fail")
                    .timestamp(now.plusMillis(i * 20))
                    .build());
        }
        assertTrue(eventBus.awaitIdle(2000), "Events should be processed");
        assertEquals(ProtectionPhase.SUSPICIOUS_ACTIVITY, engine.getPhase(SOURCE));

        engine.start();
        Thread.sleep(2000);
        assertEquals(ProtectionPhase.READY, engine.getPhase(SOURCE),
                "Monitored source should return to READY after threat decay");
    }

    @Test
    void reBlockedAfterReleaseIfStillAttacking() {
        engine.onEvent(threatEvent(ThreatLevel.CRITICAL, BASE));

        assertEquals(ProtectionAction.ALLOW, engine.evaluateRequest(SOURCE, BASE.plusSeconds(61)));
        engine.onEvent(threatEvent(ThreatLevel.CRITICAL, BASE.plusSeconds(62)));
        assertEquals(ProtectionPhase.BLOCKED, engine.getPhase(SOURCE));
        assertEquals(ProtectionAction.BLOCK, engine.evaluateRequest(SOURCE, BASE.plusSeconds(63)));
    }

    @Test
    void repeatedEscalationsDoNotDuplicateProtectionEvents() throws InterruptedException {
        engine.onEvent(threatEvent(ThreatLevel.HIGH, BASE));
        engine.onEvent(threatEvent(ThreatLevel.HIGH, BASE.plusMillis(100)));
        engine.onEvent(threatEvent(ThreatLevel.HIGH, BASE.plusMillis(200)));

        assertEquals(ProtectionPhase.PROTECTION_ENABLED, engine.getPhase(SOURCE));
        assertTrue(eventBus.awaitIdle(2000), "Events should be processed");

        long count = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.PROTECTION_ENABLED)
                .count();
        assertEquals(1, count, "Repeated HIGH escalations must not duplicate PROTECTION_ENABLED");
    }
}
