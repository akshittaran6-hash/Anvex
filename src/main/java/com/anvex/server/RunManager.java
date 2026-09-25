package com.anvex.server;

import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.persistence.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public final class RunManager {

    private static final Logger logger = LoggerFactory.getLogger(RunManager.class);

    private final DatabaseManager dbManager;
    private final EventBus eventBus;
    private volatile long currentRunId = 0;

    public RunManager(DatabaseManager dbManager, EventBus eventBus) {
        this.dbManager = dbManager;
        this.eventBus = eventBus;
    }

    public synchronized long startRun(String label) {
        if (currentRunId != 0) {
            completeRunInternal();
        }
        long runId;
        try {
            runId = insertRun(label != null && !label.isEmpty() ? label : "Unnamed Run");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to start run", e);
        }
        currentRunId = runId;
        publishRunEvent(SecurityEventType.RUN_STARTED, runId,
                String.format("Run %d started: %s", runId, label));
        logger.info("Run {} started: {}", runId, label);
        return runId;
    }

    public synchronized void endRun() {
        if (currentRunId == 0) {
            logger.warn("endRun called with no active run");
            return;
        }
        completeRunInternal();
    }

    private void completeRunInternal() {
        long runId = currentRunId;
        updateRunCompleted(runId);
        currentRunId = 0;
        publishRunEvent(SecurityEventType.RUN_COMPLETED, runId,
                String.format("Run %d completed", runId));
        logger.info("Run {} completed", runId);
    }

    public long getCurrentRunId() {
        return currentRunId;
    }

    public synchronized long ensureActiveRun() {
        return currentRunId == 0 ? startRun("Backend session") : currentRunId;
    }

    public List<RunInfo> listRuns() throws SQLException {
        String sql = "SELECT run_id, run_label, defense_enabled, start_time, end_time, status "
                + "FROM scenario_runs ORDER BY run_id DESC";
        List<RunInfo> runs = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Timestamp end = rs.getTimestamp("end_time");
                runs.add(new RunInfo(
                        rs.getLong("run_id"),
                        rs.getString("run_label"),
                        rs.getBoolean("defense_enabled"),
                        rs.getTimestamp("start_time").toInstant(),
                        end != null ? end.toInstant() : null,
                        rs.getString("status")));
            }
        }
        return runs;
    }

    public RunInfo getRun(long runId) throws SQLException {
        String sql = "SELECT run_id, run_label, defense_enabled, start_time, end_time, status "
                + "FROM scenario_runs WHERE run_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, runId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Timestamp end = rs.getTimestamp("end_time");
                    return new RunInfo(
                            rs.getLong("run_id"),
                            rs.getString("run_label"),
                            rs.getBoolean("defense_enabled"),
                            rs.getTimestamp("start_time").toInstant(),
                            end != null ? end.toInstant() : null,
                            rs.getString("status"));
                }
            }
        }
        return null;
    }

    private long insertRun(String label) throws SQLException {
        String sql = "INSERT INTO scenario_runs (run_label, defense_enabled, start_time, status) "
                + "VALUES (?, FALSE, ?, 'RUNNING')";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, label);
            stmt.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create run");
    }

    private void updateRunCompleted(long runId) {
        String sql = "UPDATE scenario_runs SET end_time = ?, status = 'COMPLETED' WHERE run_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setTimestamp(1, new Timestamp(System.currentTimeMillis()));
            stmt.setLong(2, runId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to complete run {}", runId, e);
        }
    }

    private void publishRunEvent(SecurityEventType eventType, long runId, String message) {
        eventBus.publish(SecurityEvent.builder()
                .runId(runId)
                .eventType(eventType)
                .outcome(eventType.name())
                .message(message)
                .build());
    }

    public record RunInfo(
            long runId,
            String label,
            boolean defenseEnabled,
            java.time.Instant startTime,
            java.time.Instant endTime,
            String status
    ) {}
}
