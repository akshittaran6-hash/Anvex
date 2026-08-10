package com.anvex.persistence;

import com.anvex.detection.Alert;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Persists detection alerts produced during an ANVEX run. */
public final class AlertRepository {

    private final DatabaseManager dbManager;

    public AlertRepository(DatabaseManager dbManager) {
        if (dbManager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        this.dbManager = dbManager;
    }

    public void save(Alert alert) throws SQLException {
        if (alert == null) {
            throw new IllegalArgumentException("Alert cannot be null");
        }

        String sql = """
            INSERT INTO alerts
                (run_id, rule_id, severity, timestamp, username, explanation)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, alert.runId());
            stmt.setString(2, alert.ruleId());
            stmt.setString(3, alert.severity());
            stmt.setTimestamp(4, java.sql.Timestamp.from(alert.timestamp()));
            stmt.setString(5, alert.username());
            stmt.setString(6, alert.explanation());
            stmt.executeUpdate();
        }
    }
}
