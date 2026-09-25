package com.anvex;

import com.anvex.detection.DetectionConfig;
import com.anvex.detection.DetectionEngine;
import com.anvex.detection.SourceActivityTracker;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.protection.ProtectionConfig;
import com.anvex.protection.ProtectionEngine;
import com.anvex.protection.ProtectionPhase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class EvidencePipelineTest {

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
        eventBus.subscribe(detectionEngine);
        eventBus.subscribe(engine);
    }

    @AfterEach
    void tearDown() {
        engine.stop();
        eventBus.shutdown();
    }

    private SecurityEvent loginFailure(Instant timestamp) {
        return SecurityEvent.builder()
                .runId(1)
                .eventType(SecurityEventType.LOGIN_FAILURE)
                .username("target_user")
                .clientType("ATTACKER")
                .source(SOURCE)
                .outcome("FAILURE")
                .message("Invalid password")
                .timestamp(timestamp)
                .build();
    }

    @Test
    void fullPipelineProducesEvidenceBackedDecisions() throws InterruptedException {
        Instant now = Instant.now();
        for (int i = 0; i < 20; i++) {
            eventBus.publish(loginFailure(now.plusMillis(i * 100)));
        }
        assertTrue(eventBus.awaitIdle(3000), "All events should flow through the pipeline");

        assertEquals(ProtectionPhase.BLOCKED, engine.getPhase(SOURCE), "CRITICAL escalation must block the source");

        List<SecurityEvent> anomaly = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.ANOMALY_DETECTED).toList();
        List<SecurityEvent> escalations = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.THREAT_ESCALATED).toList();
        List<SecurityEvent> protection = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.PROTECTION_ENABLED).toList();
        List<SecurityEvent> blocked = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.SOURCE_BLOCKED).toList();
        List<SecurityEvent> incidents = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.SECURITY_INCIDENT).toList();

        assertEquals(1, anomaly.size());
        assertEquals(2, escalations.size());
        assertEquals(1, protection.size());
        assertEquals(1, blocked.size());
        assertEquals(1, incidents.size(), "CRITICAL block must generate a security incident");

        for (SecurityEvent decision : List.of(anomaly.get(0), escalations.get(0), escalations.get(1),
                protection.get(0), blocked.get(0), incidents.get(0))) {
            assertNotNull(decision.getThreatLevel(), decision.getEventType() + " must carry a threat level");
            assertNotNull(decision.getEvidence(), decision.getEventType() + " must carry evidence");
            List<String> bullets = decision.getEvidence().describe();
            assertFalse(bullets.isEmpty(), decision.getEventType() + " evidence must be explainable");
        }

        SecurityEvent blockedDecision = blocked.get(0);
        assertTrue(blockedDecision.getEvidence().getFailedAttemptCount() >= 11,
                "Block evidence must state the failed attempt count");
        assertTrue(blockedDecision.getEvidence().getTimeWindowMs() > 0,
                "Block evidence must state the time window");
        assertTrue(blockedDecision.getEvidence().isThresholdExceeded(),
                "Block evidence must flag threshold exceedance");
        assertNotNull(blockedDecision.getEvidence().getRequestFrequency());
        assertTrue(incidents.get(0).getMetadata().containsKey("incidentId"), "Incident must have an ID");
    }

    @Test
    void normalActivityProducesNoDecisionEvents() throws InterruptedException {
        Instant now = Instant.now();
        for (int i = 0; i < 2; i++) {
            eventBus.publish(loginFailure(now.plusMillis(i * 100)));
        }
        assertTrue(eventBus.awaitIdle(3000), "All events should flow through the pipeline");

        assertEquals(ProtectionPhase.READY, engine.getPhase(SOURCE));

        long decisionCount = published.stream()
                .filter(e -> e.getEvidence() != null)
                .count();
        assertEquals(0, decisionCount, "No evidence-backed decisions below SUSPICIOUS");
    }
}
