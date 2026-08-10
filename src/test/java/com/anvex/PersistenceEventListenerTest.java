package com.anvex;

import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.PersistenceEventListener;
import com.anvex.persistence.SecurityEventRepository;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceEventListenerTest {

    private DatabaseManager db;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager.resetInstance();

        db = DatabaseManager.getTestInstance();
        db.initialize();
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.resetInstance();
    }

    @Test
    void persistsPublishedEvents() throws Exception {

        SecurityEventRepository repository =
                new SecurityEventRepository(db);

        PersistenceEventListener listener =
                new PersistenceEventListener(repository);

        EventBus bus = new EventBus();
        bus.subscribe(listener);

        bus.publish(
                SecurityEvent.builder()
                        .eventId("persistence-test")
                        .runId(99)
                        .eventType(SecurityEventType.LOGIN_FAILURE)
                        .username("lab_target")
                        .clientType("ATTACKER")
                        .source("TEST")
                        .outcome("FAILURE")
                        .message("Test event")
                        .build()
        );

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                 SELECT sequence_no, run_id, event_type,
                        username, client_type, message
                 FROM security_events
                 WHERE run_id = 99
                 """)) {

            try (ResultSet rs = stmt.executeQuery()) {

                assertTrue(rs.next());

                assertEquals(1, rs.getLong("sequence_no"));
                assertEquals(99, rs.getLong("run_id"));
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
                        "Test event",
                        rs.getString("message")
                );
            }
        }
    }
}
