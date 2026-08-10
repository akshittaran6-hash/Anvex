package com.anvex.persistence;

import com.anvex.event.EventListener;
import com.anvex.event.SecurityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persists canonical events and records successful persistence for run confirmation. */
public final class PersistenceEventListener implements EventListener {

    private static final Logger logger =
            LoggerFactory.getLogger(PersistenceEventListener.class);

    private final SecurityEventRepository eventRepository;
    private final RunPersistenceTracker tracker;

    public PersistenceEventListener(SecurityEventRepository eventRepository) {
        this(eventRepository, null);
    }

    public PersistenceEventListener(
            SecurityEventRepository eventRepository,
            RunPersistenceTracker tracker) {
        if (eventRepository == null) {
            throw new IllegalArgumentException("SecurityEventRepository cannot be null");
        }
        this.eventRepository = eventRepository;
        this.tracker = tracker;
    }

    @Override
    public void onEvent(SecurityEvent event) {
        if (event == null) {
            return;
        }

        if (tracker != null) {
            tracker.recordProduced(event.getRunId());
        }

        try {
            eventRepository.save(event);
            if (tracker != null) {
                tracker.recordPersisted(event.getRunId());
            }
        } catch (Exception e) {
            logger.error("Failed to persist security event {}", event.getEventId(), e);
        }
    }
}
