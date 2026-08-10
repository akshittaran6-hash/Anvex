package com.anvex.persistence;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Tracks produced and successfully persisted events independently for each run. */
public final class RunPersistenceTracker {

    private final ConcurrentHashMap<Long, AtomicLong> produced = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicLong> persisted = new ConcurrentHashMap<>();

    public void recordProduced(long runId) {
        counter(produced, runId).incrementAndGet();
    }

    public void recordPersisted(long runId) {
        counter(persisted, runId).incrementAndGet();
    }

    public long producedCount(long runId) {
        return value(produced, runId);
    }

    public long persistedCount(long runId) {
        return value(persisted, runId);
    }

    public boolean isConfirmed(long runId) {
        return producedCount(runId) == persistedCount(runId);
    }

    public void clear(long runId) {
        produced.remove(runId);
        persisted.remove(runId);
    }

    private static AtomicLong counter(
            ConcurrentHashMap<Long, AtomicLong> counters,
            long runId) {
        return counters.computeIfAbsent(runId, ignored -> new AtomicLong());
    }

    private static long value(
            ConcurrentHashMap<Long, AtomicLong> counters,
            long runId) {
        AtomicLong counter = counters.get(runId);
        return counter == null ? 0 : counter.get();
    }
}
