package com.anvex.detection;

import com.anvex.event.SecurityEvent;

import java.util.Optional;

/** A rule that examines security events and may produce an alert. */
public interface DetectionRule {
    String getRuleId();

    Optional<Alert> evaluate(SecurityEvent event);

    void reset();
}
