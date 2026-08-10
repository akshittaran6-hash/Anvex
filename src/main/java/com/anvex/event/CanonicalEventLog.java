package com.anvex.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CanonicalEventLog {

    private static final Logger logger = LoggerFactory.getLogger(CanonicalEventLog.class);

    private final List<SecurityEvent> events = new CopyOnWriteArrayList<>();
    private long currentRunId = -1;

    public void record(SecurityEvent event) {
        events.add(event);
        logger.trace("Recorded event: {}", event);
    }

    public List<SecurityEvent> getEventsForRun(long runId) {
        List<SecurityEvent> result = new ArrayList<>();
        for (SecurityEvent event : events) {
            if (event.getRunId() == runId) {
                result.add(event);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<SecurityEvent> getAllEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public void setCurrentRunId(long runId) {
        this.currentRunId = runId;
    }

    public long getCurrentRunId() {
        return currentRunId;
    }

    public void clearRun(long runId) {
        events.removeIf(e -> e.getRunId() == runId);
    }

    public void clearAll() {
        events.clear();
        currentRunId = -1;
    }

    public int size() {
        return events.size();
    }
}