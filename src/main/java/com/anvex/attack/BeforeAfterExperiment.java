package com.anvex.attack;

import com.anvex.defense.DefenseStrategy;
import com.anvex.event.CanonicalEventLog;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.monitoring.ComparisonResult;
import com.anvex.monitoring.MetricsCollector;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.ScenarioRunRepository;
import com.anvex.server.AuthenticationServer;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the canonical ANVEX before/after experiment.
 */
public final class BeforeAfterExperiment {

    private final DatabaseManager dbManager;
    private final AuthenticationServer server;
    private final EventBus eventBus;
    private final CanonicalEventLog eventLog;
    private final int workerCount;

    public BeforeAfterExperiment(
            DatabaseManager dbManager,
            AuthenticationServer server,
            EventBus eventBus,
            CanonicalEventLog eventLog,
            int workerCount
    ) {
        if (dbManager == null || server == null || eventBus == null || eventLog == null) {
            throw new IllegalArgumentException("Experiment dependencies cannot be null");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }

        this.dbManager = dbManager;
        this.server = server;
        this.eventBus = eventBus;
        this.eventLog = eventLog;
        this.workerCount = workerCount;
    }

    public ComparisonResult run(
            LoginAttackScenario scenario,
            DefenseStrategy defense
    ) throws Exception {
        if (scenario == null || defense == null) {
            throw new IllegalArgumentException("Scenario and defense cannot be null");
        }

        ScenarioRunRepository runs = new ScenarioRunRepository(dbManager);

        RunResult before = executeRun(
                runs,
                scenario,
                false,
                null,
                "DEFENSE_OFF"
        );

        RunResult after = executeRun(
                runs,
                scenario,
                true,
                defense,
                "DEFENSE_ON"
        );

        return new ComparisonResult(
                before.runId(),
                after.runId(),
                before.metrics(),
                after.metrics()
        );
    }

    private RunResult executeRun(
            ScenarioRunRepository runs,
            LoginAttackScenario scenario,
            boolean defenseEnabled,
            DefenseStrategy defense,
            String label
    ) throws Exception {

        long runId = runs.createRun(label, defenseEnabled);

        MetricsCollector metrics = new MetricsCollector();
        eventBus.subscribe(metrics::onEvent);

        try {
            server.resetDefense();
            server.resetDetection();

            if (defenseEnabled) {
                server.enableDefense(defense);
            } else {
                server.disableDefense();
            }

            server.beginRun(runId);
            eventLog.setCurrentRunId(runId);

            publish(
                    runId,
                    SecurityEventType.RUN_STARTED,
                    "RUN",
                    "STARTED",
                    "Experiment run started"
            );

            publish(
                    runId,
                    SecurityEventType.ATTACK_STARTED,
                    "ATTACK",
                    "STARTED",
                    "Attack scenario started"
            );

            AttackRunner runner = new AttackRunner(
                    scenario,
                    new AttackClientSimulator(),
                    workerCount
            );

            AttackRunner.Result attackResult = runner.run();

            // Critical: wait for all asynchronous socket handlers.
            server.awaitRequestDrain();

            publish(
                    runId,
                    SecurityEventType.ATTACK_COMPLETED,
                    "ATTACK",
                    "COMPLETED",
                    "Attack scenario completed"
            );

            publish(
                    runId,
                    SecurityEventType.RUN_COMPLETED,
                    "RUN",
                    "COMPLETED",
                    "Experiment run completed"
            );

            server.awaitRequestDrain();

            MetricsCollector.MetricsSnapshot snapshot = metrics.snapshot();

            validateAccounting(runId, attackResult, snapshot);

            runs.saveMetrics(runId, snapshot);
            runs.completeRun(runId, "COMPLETED", "CONFIRMED");

            return new RunResult(runId, snapshot);

        } catch (Exception e) {
            runs.completeRun(runId, "FAILED", "AT_RISK");
            throw e;
        } finally {
            eventBus.unsubscribe(metrics::onEvent);
        }
    }

    private void validateAccounting(
            long runId,
            AttackRunner.Result attackResult,
            MetricsCollector.MetricsSnapshot metrics
    ) {
        List<SecurityEvent> events = eventLog.getEventsForRun(runId);

        long loginAttempts = events.stream()
                .filter(e ->
                        e.getClientType() != null
                                && "ATTACKER".equals(e.getClientType())
                                && (e.getEventType() == SecurityEventType.LOGIN_FAILURE
                                || e.getEventType() == SecurityEventType.LOGIN_SUCCESS
                                || e.getEventType() == SecurityEventType.LOGIN_BLOCKED))
                .count();

        if (loginAttempts != attackResult.getTotalAttempts()) {
            throw new IllegalStateException(
                    "Event accounting mismatch for run " + runId
                            + ": attack=" + attackResult.getTotalAttempts()
                            + ", events=" + loginAttempts
            );
        }

        long failures = events.stream()
                .filter(e -> e.getEventType() == SecurityEventType.LOGIN_FAILURE)
                .filter(e -> "ATTACKER".equals(e.getClientType()))
                .count();

        long successes = events.stream()
                .filter(e -> e.getEventType() == SecurityEventType.LOGIN_SUCCESS)
                .filter(e -> "ATTACKER".equals(e.getClientType()))
                .count();

        long blocked = events.stream()
                .filter(e -> e.getEventType() == SecurityEventType.LOGIN_BLOCKED)
                .filter(e -> "ATTACKER".equals(e.getClientType()))
                .count();

        if (failures != metrics.attackerFailures()
                || successes != metrics.attackerSuccesses()
                || blocked != metrics.attackerBlocked()) {
            throw new IllegalStateException(
                    "Metrics do not match canonical event stream for run " + runId
            );
        }
    }

    private void publish(
            long runId,
            SecurityEventType type,
            String source,
            String outcome,
            String message
    ) {
        eventBus.publish(
                SecurityEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .runId(runId)
                        .eventType(type)
                        .source(source)
                        .outcome(outcome)
                        .message(message)
                        .build()
        );
    }

    private record RunResult(
            long runId,
            MetricsCollector.MetricsSnapshot metrics
    ) {}
}
