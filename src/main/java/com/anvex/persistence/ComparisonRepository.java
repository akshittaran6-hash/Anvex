package com.anvex.persistence;

import com.anvex.monitoring.ComparisonResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Persists the relationship between a defense-off and defense-on run. */
public final class ComparisonRepository {

    private final DatabaseManager dbManager;

    public ComparisonRepository(DatabaseManager dbManager) {
        if (dbManager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        this.dbManager = dbManager;
    }

    public long save(ComparisonResult comparison) throws SQLException {
        if (comparison == null) {
            throw new IllegalArgumentException("Comparison cannot be null");
        }

        String sql = """
            INSERT INTO comparison_results
                (before_run_id, after_run_id)
            VALUES (?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, comparison.beforeRunId());
            stmt.setLong(2, comparison.afterRunId());
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Failed to generate comparison ID");
                }
                return keys.getLong(1);
            }
        }
    }
}
