package com.anvex.persistence;

import com.anvex.event.SecurityEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class SecurityEventRepository {

    private final DatabaseManager dbManager;

    public SecurityEventRepository(DatabaseManager dbManager) {
        if (dbManager == null) {
            throw new IllegalArgumentException("DatabaseManager cannot be null");
        }
        this.dbManager = dbManager;
    }

    public void save(SecurityEvent event) throws SQLException {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        String sql = """
            INSERT INTO security_events
                (sequence_no, run_id, timestamp, event_type,
                 username, client_type, message)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, event.getSequenceNumber());
            stmt.setLong(2, event.getRunId());
            stmt.setTimestamp(
                    3,
                    java.sql.Timestamp.from(event.getTimestamp())
            );
            stmt.setString(
                    4,
                    event.getEventType().name()
            );
            stmt.setString(5, event.getUsername());
            stmt.setString(6, event.getClientType());
            stmt.setString(7, event.getMessage());

            stmt.executeUpdate();
        }
    }
}
