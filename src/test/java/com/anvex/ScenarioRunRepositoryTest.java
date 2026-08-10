package com.anvex;

import com.anvex.monitoring.MetricsCollector;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.ScenarioRunRepository;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioRunRepositoryTest {

    private DatabaseManager db;
    private ScenarioRunRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager.resetInstance();
        db = DatabaseManager.getTestInstance();
        db.initialize();
        repository = new ScenarioRunRepository(db);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.resetInstance();
    }

    @Test
    void createsAndCompletesRun() throws Exception {
        long runId = repository.createRun(
                "TEST_RUN",
                true
        );

        assertTrue(runId > 0);

        repository.completeRun(
                runId,
                "COMPLETED",
                "PERSISTED"
        );

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT status, persistence_status FROM scenario_runs WHERE run_id = ?")) {

            stmt.setLong(1, runId);

            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("COMPLETED", rs.getString("status"));
                assertEquals("PERSISTED", rs.getString("persistence_status"));
            }
        }
    }

    @Test
    void savesMetrics() throws Exception {
        long runId = repository.createRun(
                "METRICS_TEST",
                false
        );

        MetricsCollector metrics = new MetricsCollector();

        repository.saveMetrics(
                runId,
                metrics.snapshot()
        );

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT run_id FROM metrics_snapshots WHERE run_id = ?")) {

            stmt.setLong(1, runId);

            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(runId, rs.getLong("run_id"));
            }
        }
    }
}
