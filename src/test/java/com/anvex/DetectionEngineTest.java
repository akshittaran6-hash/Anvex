package com.anvex;

import com.anvex.detection.Alert;
import com.anvex.detection.DetectionEngine;
import com.anvex.detection.rules.RepeatedFailedLoginRule;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DetectionEngineTest {

    @Test
    void detectsExactlyOneAlertAtThreshold() {
        DetectionEngine engine = new DetectionEngine(List.of(new RepeatedFailedLoginRule()));
        List<Alert> observed = new ArrayList<>();
        engine.setAlertListener(observed::add);

        for (int i = 1; i <= 6; i++) {
            engine.onEvent(failedLogin(42, "lab_target"));
        }

        assertEquals(1, engine.getAlertCount());
        assertEquals(1, observed.size());
        assertEquals(RepeatedFailedLoginRule.RULE_ID, observed.get(0).ruleId());
        assertEquals("lab_target", observed.get(0).username());
    }

    @Test
    void tracksAccountsAndRunsIndependently() {
        DetectionEngine engine = new DetectionEngine(List.of(new RepeatedFailedLoginRule()));

        for (int i = 0; i < 4; i++) {
            engine.onEvent(failedLogin(1, "alice"));
            engine.onEvent(failedLogin(2, "bob"));
        }

        assertEquals(0, engine.getAlertCount());

        engine.onEvent(failedLogin(1, "alice"));
        assertEquals(1, engine.getAlertCount());

        engine.onEvent(failedLogin(2, "bob"));
        assertEquals(2, engine.getAlertCount());
    }

    @Test
    void ignoresSuccessfulLogins() {
        DetectionEngine engine = new DetectionEngine(List.of(new RepeatedFailedLoginRule()));

        for (int i = 0; i < 10; i++) {
            engine.onEvent(SecurityEvent.builder()
                    .runId(1)
                    .eventType(SecurityEventType.LOGIN_SUCCESS)
                    .username("alice")
                    .build());
        }

        assertEquals(0, engine.getAlertCount());
    }

    @Test
    void resetClearsDetectionState() {
        DetectionEngine engine = new DetectionEngine(List.of(new RepeatedFailedLoginRule()));

        for (int i = 0; i < 5; i++) {
            engine.onEvent(failedLogin(1, "alice"));
        }
        assertEquals(1, engine.getAlertCount());

        engine.reset();
        assertEquals(0, engine.getAlertCount());

        for (int i = 0; i < 4; i++) {
            engine.onEvent(failedLogin(1, "alice"));
        }
        assertEquals(0, engine.getAlertCount());
    }

    @Test
    void alertCanBePublishedBackToCanonicalEventStream() {
        DetectionEngine engine = new DetectionEngine(List.of(new RepeatedFailedLoginRule()));
        EventBus bus = new EventBus();
        List<SecurityEvent> events = new ArrayList<>();
        bus.subscribe(events::add);
        engine.setAlertListener(alert -> bus.publish(DetectionEngine.toAlertEvent(alert)));

        for (int i = 0; i < 5; i++) {
            engine.onEvent(failedLogin(7, "target"));
        }

        assertEquals(1, events.size());
        assertEquals(SecurityEventType.ALERT_TRIGGERED, events.get(0).getEventType());
        assertEquals("target", events.get(0).getUsername());
        assertEquals(RepeatedFailedLoginRule.RULE_ID, events.get(0).getSource());
    }

    private static SecurityEvent failedLogin(long runId, String username) {
        return SecurityEvent.builder()
                .runId(runId)
                .eventType(SecurityEventType.LOGIN_FAILURE)
                .username(username)
                .clientType("ATTACKER")
                .source("AUTHENTICATION_SERVER")
                .outcome("FAILURE")
                .message("Invalid credentials")
                .build();
    }
}
