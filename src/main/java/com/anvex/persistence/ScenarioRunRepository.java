package com.anvex.persistence;

import com.anvex.monitoring.MetricsCollector;

import java.sql.*;

public final class ScenarioRunRepository {

    private final DatabaseManager dbManager;

    public ScenarioRunRepository(DatabaseManager dbManager) {
        if (dbManager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        this.dbManager = dbManager;
    }

    public long createRun(
            String runLabel,
            boolean defenseEnabled
    ) throws SQLException {

        String sql = """
            INSERT INTO scenario_runs
                (run_label, defense_enabled, start_time, status, persistence_status)
            VALUES (?, ?, CURRENT_TIMESTAMP, 'RUNNING', 'PENDING')
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     sql,
                     Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, runLabel);
            stmt.setBoolean(2, defenseEnabled);
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to generate run ID");
                }
                return keys.getLong(1);
            }
        }
    }

    public void completeRun(
            long runId,
            String status,
            String persistenceStatus
    ) throws SQLException {

        String sql = """
            UPDATE scenario_runs
            SET end_time = CURRENT_TIMESTAMP,
                status = ?,
                persistence_status = ?
            WHERE run_id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status);
            stmt.setString(2, persistenceStatus);
            stmt.setLong(3, runId);

            int updated = stmt.executeUpdate();

            if (updated != 1) {
                throw new SQLException(
                        "Run not found: " + runId
                );
            }
        }
    }

    public void saveMetrics(
            long runId,
            MetricsCollector.MetricsSnapshot metrics
    ) throws SQLException {

        String sql = """
            INSERT INTO metrics_snapshots
                (run_id, total_attempts, failures, blocked,
                 attacker_success, legitimate_success,
                 compromised, duration_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, runId);
            stmt.setLong(2, metrics.totalAttackerAttempts());
            stmt.setLong(3, metrics.attackerFailures());
            stmt.setLong(4, metrics.attackerBlocked());
            stmt.setLong(5, metrics.attackerSuccesses());
            stmt.setLong(6, metrics.legitimateSuccesses());
            stmt.setBoolean(7, metrics.accountCompromised());
            stmt.setLong(8, metrics.durationMs());

            stmt.executeUpdate();
        }
    }
}
