package com.anvex;

import com.anvex.monitoring.ComparisonResult;
import com.anvex.monitoring.MetricsCollector;
import com.anvex.persistence.ComparisonRepository;
import com.anvex.persistence.DatabaseManager;
import org.junit.jupiter.api.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

class ComparisonRepositoryTest {

    private DatabaseManager db;
    private ComparisonRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager.resetInstance();
        db = DatabaseManager.getTestInstance();
        db.initialize();
        repository = new ComparisonRepository(db);
    }

    @AfterEach
    void tearDown() {
        DatabaseManager.resetInstance();
    }

    @Test
    void savesComparison() throws Exception {
        MetricsCollector.MetricsSnapshot before =
                new MetricsCollector.MetricsSnapshot(2000, 1999, 0, 1, 1, false, 100);
        MetricsCollector.MetricsSnapshot after =
                new MetricsCollector.MetricsSnapshot(2000, 5, 1995, 0, 1, false, 120);

        ComparisonResult comparison = new ComparisonResult(11, 12, before, after);
        long comparisonId = repository.save(comparison);

        assertTrue(comparisonId > 0);

        try (Connection conn = db.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT before_run_id, after_run_id FROM comparison_results WHERE comparison_id = ?")) {
            stmt.setLong(1, comparisonId);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(11, rs.getLong("before_run_id"));
                assertEquals(12, rs.getLong("after_run_id"));
            }
        }
    }
}
