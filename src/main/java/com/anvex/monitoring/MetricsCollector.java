package com.anvex.monitoring;

import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class MetricsCollector {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCollector.class);

    private final AtomicLong totalAttackerAttempts = new AtomicLong(0);
    private final AtomicLong attackerFailures = new AtomicLong(0);
    private final AtomicLong attackerBlocked = new AtomicLong(0);
    private final AtomicLong attackerSuccesses = new AtomicLong(0);
    private final AtomicLong legitimateSuccesses = new AtomicLong(0);
    private final AtomicLong legitimateFailures = new AtomicLong(0);
    private final AtomicLong alertCount = new AtomicLong(0);
    private final AtomicInteger accountCompromised = new AtomicInteger(0);
    private final AtomicInteger accountLocked = new AtomicInteger(0);
    private final AtomicLong runStartTime = new AtomicLong(0);
    private final AtomicLong runEndTime = new AtomicLong(0);

    public void onEvent(SecurityEvent event) {
        SecurityEventType type = event.getEventType();
        String clientType = event.getClientType();
        String outcome = event.getOutcome();

        switch (type) {
            case ATTACK_STARTED, RUN_STARTED -> runStartTime.set(event.getTimestamp().toEpochMilli());
            case ATTACK_COMPLETED -> runEndTime.set(System.currentTimeMillis());
            case LOGIN_SUCCESS -> {
                if ("ATTACKER".equals(clientType)) {
                    attackerSuccesses.incrementAndGet();
                    if ("lab_target".equals(event.getUsername())) {
                        accountCompromised.set(1);
                    }
                } else {
                    legitimateSuccesses.incrementAndGet();
                }
            }
            case LOGIN_FAILURE -> {
                if ("ATTACKER".equals(clientType)) {
                    attackerFailures.incrementAndGet();
                } else {
                    legitimateFailures.incrementAndGet();
                }
            }
            case LOGIN_BLOCKED -> {
                if ("ATTACKER".equals(clientType)) {
                    attackerBlocked.incrementAndGet();
                }
            }
            case ALERT_TRIGGERED, SECURITY_INCIDENT -> alertCount.incrementAndGet();
            case ACCOUNT_LOCKED -> accountLocked.set(1);
            case RUN_COMPLETED -> runEndTime.set(System.currentTimeMillis());
        }

        if (type == SecurityEventType.LOGIN_FAILURE || type == SecurityEventType.LOGIN_SUCCESS
                || type == SecurityEventType.LOGIN_BLOCKED) {
            if ("ATTACKER".equals(clientType)) {
                totalAttackerAttempts.incrementAndGet();
            }
        }
    }

    public MetricsSnapshot snapshot() {
        return new MetricsSnapshot(
                totalAttackerAttempts.get(),
                attackerFailures.get(),
                attackerBlocked.get(),
                attackerSuccesses.get(),
                legitimateSuccesses.get(),
                legitimateFailures.get(),
                alertCount.get(),
                accountCompromised.get() == 1,
                accountLocked.get() == 1,
                runStartTime.get() == 0 ? 0 : Math.max(0,
                        (runEndTime.get() == 0 ? System.currentTimeMillis() : runEndTime.get()) - runStartTime.get())
        );
    }

    public void reset() {
        totalAttackerAttempts.set(0);
        attackerFailures.set(0);
        attackerBlocked.set(0);
        attackerSuccesses.set(0);
        legitimateSuccesses.set(0);
        legitimateFailures.set(0);
        alertCount.set(0);
        accountCompromised.set(0);
        accountLocked.set(0);
        runStartTime.set(0);
        runEndTime.set(0);
    }

    public record MetricsSnapshot(
            long totalAttackerAttempts,
            long attackerFailures,
            long attackerBlocked,
            long attackerSuccesses,
            long legitimateSuccesses,
            long legitimateFailures,
            long alertCount,
            boolean accountCompromised,
            boolean accountLocked,
            long durationMs
    ) {}
}
