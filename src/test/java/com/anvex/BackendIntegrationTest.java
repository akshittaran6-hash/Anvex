package com.anvex;

import com.anvex.detection.DetectionConfig;
import com.anvex.detection.DetectionEngine;
import com.anvex.detection.SourceActivityTracker;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.event.ThreatLevel;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.EventRepository;
import com.anvex.persistence.EventRepository.PersistedAlert;
import com.anvex.persistence.EventRepository.PersistedEvent;
import com.anvex.persistence.UserRepository;
import com.anvex.protection.ProtectionConfig;
import com.anvex.protection.ProtectionEngine;
import com.anvex.protection.ProtectionPhase;
import com.anvex.server.AuthenticationServer;
import com.anvex.util.AppConfig;
import com.anvex.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackendIntegrationTest {

    private static DatabaseManager dbManager;
    private static EventBus eventBus;
    private static DetectionEngine detectionEngine;
    private static ProtectionEngine protectionEngine;
    private static EventRepository eventRepository;
    private static AuthenticationServer server;
    private static UserRepository userRepository;

    private static final String ATTACK_SOURCE = "10.0.0.1";
    private static final String SECOND_ATTACK_SOURCE = "10.0.0.2";
    private static final String CLEAN_SOURCE = "10.0.0.3";

    @BeforeAll
    static void setUp() throws Exception {
        DatabaseManager.resetInstance();
        dbManager = DatabaseManager.getTestInstance();
        dbManager.initialize();

        eventBus = new EventBus();

        detectionEngine = new DetectionEngine(new DetectionConfig(), new SourceActivityTracker(), eventBus);
        eventBus.subscribe(detectionEngine);

        ProtectionConfig protectionConfig = new ProtectionConfig();
        protectionConfig.setHighDelayMs(200);
        protectionEngine = new ProtectionEngine(detectionEngine, protectionConfig, eventBus);
        eventBus.subscribe(protectionEngine);

        eventRepository = new EventRepository(dbManager);
        eventBus.subscribe(eventRepository);

        userRepository = new UserRepository(dbManager);
        userRepository.createUser("lab_target", PasswordUtil.hashPassword("target123"), "TARGET");

        server = new AuthenticationServer(dbManager, eventBus, protectionEngine);
        server.start();
        Thread.sleep(500);
    }

    @AfterAll
    static void tearDown() {
        if (server != null) server.stop();
        if (protectionEngine != null) protectionEngine.stop();
        if (eventBus != null) eventBus.shutdown();
        DatabaseManager.resetInstance();
    }

    private String sendLogin(String username, String password, String clientType, String sourceId)
            throws IOException {
        try (Socket socket = new Socket(AppConfig.SERVER_HOST, AppConfig.SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("LOGIN|" + username + "|" + password + "|" + clientType + "|" + sourceId);
            String response = in.readLine();
            return response != null ? response : "ERROR|No response";
        }
    }

    private void sendAttackBurst(String sourceId, int count) throws IOException, InterruptedException {
        for (int i = 0; i < count; i++) {
            sendLogin("lab_target", "wrong-guess-" + i, "ATTACKER", sourceId);
            Thread.sleep(20);
        }
        assertTrue(eventBus.awaitIdle(10_000), "All events should be processed");
    }

    @Test
    void attackBurstGetsBlockedAndDecisionsPersisted() throws Exception {
        sendAttackBurst(ATTACK_SOURCE, 20);

        assertEquals(ProtectionPhase.BLOCKED, protectionEngine.getPhase(ATTACK_SOURCE),
                "Rapid attack burst must escalate to CRITICAL and block the source");

        List<PersistedEvent> persistedEvents = eventRepository.findBySource(ATTACK_SOURCE);
        long blockedEvents = persistedEvents.stream()
                .filter(e -> "BLOCKED".equals(e.outcome()))
                .count();
        assertTrue(blockedEvents >= 1, "Attack burst must contain blocked responses");

        List<PersistedEvent> sourceBlockedDecisions = persistedEvents.stream()
                .filter(e -> SecurityEventType.SOURCE_BLOCKED.name().equals(e.eventType()))
                .toList();
        assertEquals(1, sourceBlockedDecisions.size());
        PersistedEvent blockedDecision = sourceBlockedDecisions.get(0);
        assertEquals(ThreatLevel.CRITICAL, blockedDecision.threatLevel());
        assertNotNull(blockedDecision.evidence(), "Persisted block decision must carry evidence");
        assertTrue(blockedDecision.evidence().getFailedAttemptCount() >= 11);
        assertTrue(blockedDecision.evidence().isThresholdExceeded());

        List<PersistedAlert> alerts = eventRepository.findAlertsForRun(1);
        assertTrue(alerts.stream().anyMatch(a -> "SOURCE_BLOCKED".equals(a.ruleId())),
                "Block decision must be persisted as an alert");
        assertTrue(alerts.stream().anyMatch(a -> "SECURITY_INCIDENT".equals(a.ruleId())),
                "CRITICAL block must be persisted as a security incident");
        assertTrue(alerts.stream().anyMatch(a -> a.explanation() != null && a.explanation().contains("failed login attempts")),
                "Alert explanations must contain evidence bullets");
    }

    @Test
    void legitUserFromDifferentSourceStillAllowedWhileOtherSourceBlocked() throws Exception {
        sendAttackBurst(SECOND_ATTACK_SOURCE, 20);
        assertEquals(ProtectionPhase.BLOCKED, protectionEngine.getPhase(SECOND_ATTACK_SOURCE));

        String response = sendLogin("lab_target", "target123", "LEGITIMATE", CLEAN_SOURCE);
        assertEquals("SUCCESS", response,
                "A clean source must be unaffected by the block on a different source");
    }

    @Test
    void blockedSourceRefusedEvenWithCorrectPassword() throws Exception {
        protectionEngine.onEvent(SecurityEvent.builder()
                .eventType(SecurityEventType.THREAT_ESCALATED)
                .source("10.0.0.9")
                .outcome(ThreatLevel.CRITICAL.name())
                .message("test escalation")
                .threatLevel(ThreatLevel.CRITICAL)
                .build());
        eventBus.awaitIdle(2000);
        assertEquals(ProtectionPhase.BLOCKED, protectionEngine.getPhase("10.0.0.9"));

        String response = sendLogin("lab_target", "target123", "ATTACKER", "10.0.0.9");
        assertEquals("BLOCKED", response,
                "Blocked source must be refused without a credential check, even with the correct password");
    }

    @Test
    void allDecisionEventsPersistedWithEvidenceColumns() throws Exception {
        sendAttackBurst("10.0.0.44", 20);
        int eventsBefore = eventRepository.countEvents();
        assertTrue(eventsBefore > 0, "Events should be persisted from this test's attack");

        List<PersistedEvent> persisted = eventRepository.findByRun(1);
        List<PersistedEvent> decisions = persisted.stream()
                .filter(e -> e.evidence() != null)
                .toList();
        assertFalse(decisions.isEmpty(), "Decision events must exist in the database");

        for (PersistedEvent decision : decisions) {
            assertNotNull(decision.threatLevel(), "Decision events must persist a threat level");
            assertNotNull(decision.evidence().getFailedAttemptCount());
            assertTrue(decision.evidence().getTimeWindowMs() > 0);
            assertTrue(decision.evidence().getRequestFrequency() >= 0);
            assertNotNull(decision.source(), "Decision events must persist their source");
        }
    }
}
