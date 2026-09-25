package com.anvex;

import com.anvex.detection.AttemptOutcome;
import com.anvex.detection.DetectionConfig;
import com.anvex.detection.DetectionEngine;
import com.anvex.detection.SourceActivityTracker;
import com.anvex.detection.ThreatAssessment;
import com.anvex.event.Evidence;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.event.ThreatLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class DetectionEngineTest {

    private static final Instant BASE = Instant.parse("2026-01-01T00:00:00Z");
    private static final String SOURCE = "192.168.1.50";

    private DetectionConfig config;
    private SourceActivityTracker tracker;
    private EventBus eventBus;
    private DetectionEngine engine;
    private List<SecurityEvent> published;

    @BeforeEach
    void setUp() {
        config = new DetectionConfig();
        tracker = new SourceActivityTracker();
        eventBus = new EventBus();
        engine = new DetectionEngine(config, tracker, eventBus);
        published = new CopyOnWriteArrayList<>();
        eventBus.subscribe(published::add);
    }

    @AfterEach
    void tearDown() {
        eventBus.shutdown();
    }

    private void recordFailures(String sourceId, Instant start, int count, long intervalMs) {
        for (int i = 0; i < count; i++) {
            tracker.recordAttempt(sourceId, start.plusMillis(i * intervalMs), AttemptOutcome.FAILURE);
        }
    }

    private SecurityEvent loginEvent(Instant timestamp, String outcome) {
        return SecurityEvent.builder()
                .runId(1)
                .eventType("SUCCESS".equals(outcome)
                        ? SecurityEventType.LOGIN_SUCCESS
                        : ("BLOCKED".equals(outcome) ? SecurityEventType.LOGIN_BLOCKED : SecurityEventType.LOGIN_FAILURE))
                .username("target_user")
                .clientType("ATTACKER")
                .source(SOURCE)
                .outcome(outcome)
                .message("test")
                .timestamp(timestamp)
                .build();
    }

    @Test
    void singleFailureStaysNormal() {
        recordFailures(SOURCE, BASE, 1, 100);
        ThreatAssessment assessment = engine.assess(SOURCE, BASE.plusMillis(200));

        assertEquals(ThreatLevel.NORMAL, assessment.getThreatLevel());
        assertTrue(assessment.getThreatScore() <= config.getNormalBandLimit());
    }

    @Test
    void fewFailuresBecomeSuspicious() {
        recordFailures(SOURCE, BASE, 5, 100);
        ThreatAssessment assessment = engine.assess(SOURCE, BASE.plusMillis(600));

        assertEquals(ThreatLevel.SUSPICIOUS, assessment.getThreatLevel());
        assertTrue(assessment.getThreatScore() > config.getNormalBandLimit());
        assertTrue(assessment.getThreatScore() <= config.getSuspiciousBandLimit());
        assertTrue(assessment.getEvidence().isThresholdExceeded());
        assertEquals(5, assessment.getEvidence().getFailedAttemptCount());
    }

    @Test
    void repeatedFailuresReachHigh() {
        recordFailures(SOURCE, BASE, 10, 100);
        ThreatAssessment assessment = engine.assess(SOURCE, BASE.plusMillis(1100));

        assertEquals(ThreatLevel.HIGH, assessment.getThreatLevel());
        assertTrue(assessment.getThreatScore() > config.getSuspiciousBandLimit());
        assertTrue(assessment.getThreatScore() <= config.getHighBandLimit());
    }

    @Test
    void largeRapidAttackReachesCritical() {
        recordFailures(SOURCE, BASE, 20, 100);
        ThreatAssessment assessment = engine.assess(SOURCE, BASE.plusMillis(2100));

        assertEquals(ThreatLevel.CRITICAL, assessment.getThreatLevel());
        assertTrue(assessment.getThreatScore() > config.getHighBandLimit());
    }

    @Test
    void slowPersistentAttackReachesCriticalThroughVolumeRule() {
        recordFailures(SOURCE, BASE, 20, 2000);
        ThreatAssessment assessment = engine.assess(SOURCE, BASE.plusMillis(40_000));

        assertEquals(ThreatLevel.CRITICAL, assessment.getThreatLevel(),
                "A large sustained failure pattern must escalate to CRITICAL even when slow");
    }

    @Test
    void moderateSlowAttackStaysHigh() {
        recordFailures(SOURCE, BASE, 14, 2000);
        ThreatAssessment assessment = engine.assess(SOURCE, BASE.plusMillis(30_000));

        assertEquals(ThreatLevel.HIGH, assessment.getThreatLevel(),
                "Below the critical streak threshold, a slow attack must not reach CRITICAL");
        assertTrue(assessment.getThreatScore() <= config.getHighBandLimit());
    }

    @Test
    void streakResetsAfterFullRecovery() throws InterruptedException {
        DetectionConfig fastDecay = new DetectionConfig();
        fastDecay.setTimeWindowMs(200);
        fastDecay.setDecayIntervalMs(100);
        fastDecay.setDecayStepPerInterval(50);
        DetectionEngine decayEngine = new DetectionEngine(fastDecay, new SourceActivityTracker(), eventBus);
        eventBus.subscribe(decayEngine);

        Instant now = Instant.now();
        for (int i = 0; i < 15; i++) {
            decayEngine.onEvent(loginEvent(now.plusMillis(i * 20), "FAILURE"));
        }
        assertEquals(ThreatLevel.CRITICAL, decayEngine.getCurrentLevel(SOURCE));

        Thread.sleep(2000);
        assertEquals(ThreatLevel.NORMAL, decayEngine.assess(SOURCE, Instant.now()).getThreatLevel(),
                "Threat must fully decay after inactivity");

        decayEngine.onEvent(loginEvent(Instant.now(), "FAILURE"));
        decayEngine.onEvent(loginEvent(Instant.now().plusMillis(50), "FAILURE"));

        assertNotEquals(ThreatLevel.CRITICAL, decayEngine.getCurrentLevel(SOURCE),
                "Streak must reset after recovery so old attacks do not instantly re-escalate");
        assertEquals(ThreatLevel.NORMAL, decayEngine.getCurrentLevel(SOURCE),
                "Two fresh failures are normal behaviour after recovery");

        decayEngine.onEvent(loginEvent(Instant.now().plusMillis(100), "FAILURE"));
        decayEngine.onEvent(loginEvent(Instant.now().plusMillis(150), "FAILURE"));
        decayEngine.onEvent(loginEvent(Instant.now().plusMillis(200), "FAILURE"));

        assertEquals(ThreatLevel.SUSPICIOUS, decayEngine.getCurrentLevel(SOURCE),
                "Fresh escalation from a clean slate must work normally");
    }

    @Test
    void blockedAttemptsCountForDensityButNotFailures() {
        recordFailures(SOURCE, BASE, 5, 100);
        for (int i = 0; i < 15; i++) {
            tracker.recordAttempt(SOURCE, BASE.plusMillis(600 + i * 50), AttemptOutcome.BLOCKED);
        }
        ThreatAssessment assessment = engine.assess(SOURCE, BASE.plusMillis(1500));

        assertEquals(5, assessment.getEvidence().getFailedAttemptCount(), "Blocked attempts are not failed logins");
        assertTrue(assessment.getThreatScore() > config.getNormalBandLimit(), "Blocked attempts still show sustained activity");
    }

    @Test
    void successfulLoginResetsFailureStreak() {
        recordFailures(SOURCE, BASE, 5, 100);
        tracker.recordAttempt(SOURCE, BASE.plusMillis(600), AttemptOutcome.SUCCESS);
        ThreatAssessment assessment = engine.assess(SOURCE, BASE.plusMillis(700));

        assertEquals(0, tracker.getHistory(SOURCE).getConsecutiveFailures());
        assertTrue(assessment.getThreatScore() < 40, "Pattern points should drop after a success");
    }

    @Test
    void oldAttemptsAgeOutOfWindow() {
        recordFailures(SOURCE, BASE, 10, 100);
        ThreatAssessment duringAttack = engine.assess(SOURCE, BASE.plusMillis(1100));
        assertEquals(ThreatLevel.HIGH, duringAttack.getThreatLevel());

        ThreatAssessment afterWindow = engine.assess(SOURCE, BASE.plusMillis(31_500));
        assertEquals(ThreatLevel.NORMAL, afterWindow.getThreatLevel());
        assertEquals(0, afterWindow.getEvidence().getFailedAttemptCount());
    }

    @Test
    void levelDecaysGraduallyAfterInactivity() {
        Instant start = BASE;
        for (int i = 0; i < 20; i++) {
            engine.onEvent(loginEvent(start.plusMillis(i * 100), "FAILURE"));
        }
        Instant lastEvent = start.plusMillis(1900);
        assertEquals(ThreatLevel.CRITICAL, engine.getCurrentLevel(SOURCE));

        ThreatAssessment afterWindow = engine.assess(SOURCE, lastEvent.plusMillis(31_000));
        assertEquals(ThreatLevel.HIGH, afterWindow.getThreatLevel(), "Score decays as the window empties");

        ThreatAssessment later = engine.assess(SOURCE, lastEvent.plusMillis(41_000));
        assertEquals(ThreatLevel.SUSPICIOUS, later.getThreatLevel(), "Score keeps decaying with inactivity");

        ThreatAssessment recovered = engine.assess(SOURCE, lastEvent.plusMillis(61_000));
        assertEquals(ThreatLevel.NORMAL, recovered.getThreatLevel(), "System should recover after long inactivity");
    }

    @Test
    void onEventRecordsAttemptsAndPublishesEscalation() throws InterruptedException {
        engine.onEvent(loginEvent(BASE, "FAILURE"));
        engine.onEvent(loginEvent(BASE.plusMillis(100), "FAILURE"));
        engine.onEvent(loginEvent(BASE.plusMillis(200), "FAILURE"));
        engine.onEvent(loginEvent(BASE.plusMillis(300), "FAILURE"));
        engine.onEvent(loginEvent(BASE.plusMillis(400), "FAILURE"));

        assertEquals(ThreatLevel.SUSPICIOUS, engine.getCurrentLevel(SOURCE));

        engine.onEvent(loginEvent(BASE.plusMillis(500), "FAILURE"));
        engine.onEvent(loginEvent(BASE.plusMillis(600), "FAILURE"));
        engine.onEvent(loginEvent(BASE.plusMillis(700), "FAILURE"));

        assertEquals(ThreatLevel.HIGH, engine.getCurrentLevel(SOURCE));
        assertTrue(eventBus.awaitIdle(2000), "Events should be processed");

        List<SecurityEvent> anomalies = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.ANOMALY_DETECTED)
                .toList();
        List<SecurityEvent> escalations = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.THREAT_ESCALATED)
                .toList();

        assertEquals(1, anomalies.size(), "One ANOMALY_DETECTED when entering SUSPICIOUS");
        assertEquals(1, escalations.size(), "One THREAT_ESCALATED when entering HIGH");
        assertEquals(ThreatLevel.HIGH, escalations.get(0).getThreatLevel());
        assertNotNull(escalations.get(0).getEvidence());
        assertTrue(escalations.get(0).getEvidence().getFailedAttemptCount() >= 6);
    }

    @Test
    void onEventDoesNotRepublishForNonEscalatingEvents() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            engine.onEvent(loginEvent(BASE.plusMillis(i * 100), "FAILURE"));
        }
        assertEquals(ThreatLevel.HIGH, engine.getCurrentLevel(SOURCE));
        assertTrue(eventBus.awaitIdle(2000), "Events should be processed");

        long escalationCount = published.stream()
                .filter(e -> e.getEventType() == SecurityEventType.THREAT_ESCALATED)
                .count();
        assertEquals(1, escalationCount, "Staying in HIGH must not republish escalation");
    }
}
