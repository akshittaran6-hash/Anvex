package com.anvex;

import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.SecurityEventRepository;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SecurityEventRepositoryTest {

    private DatabaseManager db;
    private SecurityEventRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager.resetInstance();
        db = DatabaseManager.getTestInstance();
        db.initialize();
        repository = new SecurityEventRepository(db);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.resetInstance();
    }

    @Test
    void savesSecurityEvent() throws Exception {
        SecurityEvent event = SecurityEvent.builder()
                .sequenceNumber(42)
                .eventId("event-42")
                .runId(7)
                .timestamp(Instant.now())
                .eventType(SecurityEventType.LOGIN_FAILURE)
                .username("lab_target")
                .clientType("ATTACKER")
                .source("AUTHENTICATION_SERVER")
                .outcome("FAILURE")
                .message("Invalid credentials")
                .build();

        repository.save(event);

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT sequence_no, run_id, event_type,
                        username, client_type, message
                 FROM security_events
                 WHERE run_id = ?
                 """)) {

            stmt.setLong(1, 7);

            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());

                assertEquals(42, rs.getLong("sequence_no"));
                assertEquals(7, rs.getLong("run_id"));
                assertEquals(
                        "LOGIN_FAILURE",
                        rs.getString("event_type")
                );
                assertEquals(
                        "lab_target",
                        rs.getString("username")
                );
                assertEquals(
                        "ATTACKER",
                        rs.getString("client_type")
                );
                assertEquals(
                        "Invalid credentials",
                        rs.getString("message")
                );
            }
        }
    }
}
