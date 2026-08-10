package com.anvex.detection;

import com.anvex.event.EventListener;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Coordinates detection rules and publishes detection alerts as security events. */
public final class DetectionEngine implements EventListener {
    private static final Logger logger = LoggerFactory.getLogger(DetectionEngine.class);

    private final List<DetectionRule> rules = new CopyOnWriteArrayList<>();
    private final List<Alert> alerts = new CopyOnWriteArrayList<>();
    private final List<AlertListener> alertListeners = new CopyOnWriteArrayList<>();

    public DetectionEngine() {
    }

    public DetectionEngine(List<DetectionRule> initialRules) {
        if (initialRules != null) {
            initialRules.forEach(this::addRule);
        }
    }

    public void addRule(DetectionRule rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Detection rule cannot be null");
        }
        rules.add(rule);
    }

    /** Replaces all alert listeners; retained for compatibility with existing callers. */
    public void setAlertListener(AlertListener listener) {
        alertListeners.clear();
        if (listener != null) {
            alertListeners.add(listener);
        }
    }

    public void addAlertListener(AlertListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Alert listener cannot be null");
        }
        alertListeners.add(listener);
    }

    public void removeAlertListener(AlertListener listener) {
        if (listener != null) {
            alertListeners.remove(listener);
        }
    }

    @Override
    public void onEvent(SecurityEvent event) {
        if (event == null) {
            return;
        }

        for (DetectionRule rule : rules) {
            try {
                rule.evaluate(event).ifPresent(this::recordAlert);
            } catch (RuntimeException e) {
                logger.error("Detection rule {} failed", rule.getRuleId(), e);
            }
        }
    }

    private void recordAlert(Alert alert) {
        alerts.add(alert);
        logger.warn("Detection alert {} for {}: {}", alert.ruleId(), alert.username(), alert.explanation());

        for (AlertListener listener : alertListeners) {
            try {
                listener.onAlert(alert);
            } catch (RuntimeException e) {
                logger.error("Alert listener failed for {}", alert.alertId(), e);
            }
        }
    }

    public List<Alert> getAlerts() {
        return Collections.unmodifiableList(new ArrayList<>(alerts));
    }

    public long getAlertCount() {
        return alerts.size();
    }

    public void reset() {
        alerts.clear();
        rules.forEach(DetectionRule::reset);
    }

    /** Called by the application to turn an alert into the canonical event stream. */
    public static SecurityEvent toAlertEvent(Alert alert) {
        return SecurityEvent.builder()
                .eventId(alert.alertId())
                .runId(alert.runId())
                .timestamp(alert.timestamp())
                .eventType(SecurityEventType.ALERT_TRIGGERED)
                .username(alert.username())
                .clientType("SYSTEM")
                .source(alert.ruleId())
                .outcome(alert.severity())
                .message(alert.explanation())
                .metadata(java.util.Map.of(
                        "ruleId", alert.ruleId(),
                        "evidence", alert.evidence(),
                        "suggestedDefense", alert.suggestedDefense()))
                .build();
    }

    @FunctionalInterface
    public interface AlertListener {
        void onAlert(Alert alert);
    }
}
