package com.anvex;

import com.anvex.persistence.RunPersistenceTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunPersistenceTrackerTest {

    @Test
    void confirmsOnlyWhenProducedEqualsPersisted() {
        RunPersistenceTracker tracker = new RunPersistenceTracker();

        tracker.recordProduced(42);
        tracker.recordProduced(42);
        assertFalse(tracker.isConfirmed(42));

        tracker.recordPersisted(42);
        assertFalse(tracker.isConfirmed(42));

        tracker.recordPersisted(42);
        assertTrue(tracker.isConfirmed(42));
        assertEquals(2, tracker.producedCount(42));
        assertEquals(2, tracker.persistedCount(42));
    }

    @Test
    void tracksRunsIndependently() {
        RunPersistenceTracker tracker = new RunPersistenceTracker();

        tracker.recordProduced(1);
        tracker.recordPersisted(1);
        tracker.recordProduced(2);

        assertTrue(tracker.isConfirmed(1));
        assertFalse(tracker.isConfirmed(2));
    }
}
