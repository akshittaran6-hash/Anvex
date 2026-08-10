package com.anvex;

import com.anvex.detection.Alert;
import com.anvex.persistence.AlertRepository;
import com.anvex.persistence.DatabaseManager;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AlertRepositoryTest {

    private DatabaseManager db;
    private AlertRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager.resetInstance();
        db = DatabaseManager.getTestInstance();
        db.initialize();
        repository = new AlertRepository(db);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.resetInstance();
    }

    @Test
    void savesAlert() throws Exception {
        Alert alert = new Alert(
                "alert-1",
                7,
                "REPEATED_FAILED_LOGIN",
                "HIGH",
                Instant.now(),
                "lab_target",
                "5 failed attempts",
                "Repeated failures detected",
                "Enable account lockout");

        repository.save(alert);

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT run_id, rule_id, severity, username, explanation FROM alerts WHERE run_id = ?")) {
            stmt.setLong(1, 7);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("REPEATED_FAILED_LOGIN", rs.getString("rule_id"));
                assertEquals("HIGH", rs.getString("severity"));
                assertEquals("lab_target", rs.getString("username"));
                assertEquals("Repeated failures detected", rs.getString("explanation"));
            }
        }
    }
}
