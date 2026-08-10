package com.anvex.persistence;

import com.anvex.event.EventListener;
import com.anvex.event.SecurityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PersistenceEventListener implements EventListener {

    private static final Logger logger =
            LoggerFactory.getLogger(PersistenceEventListener.class);

    private final SecurityEventRepository eventRepository;

    public PersistenceEventListener(
            SecurityEventRepository eventRepository) {

        if (eventRepository == null) {
            throw new IllegalArgumentException(
                    "SecurityEventRepository cannot be null"
            );
        }

        this.eventRepository = eventRepository;
    }

    @Override
    public void onEvent(SecurityEvent event) {
        try {
            eventRepository.save(event);
        } catch (Exception e) {
            logger.error(
                    "Failed to persist security event {}",
                    event.getEventId(),
                    e
            );
        }
    }
}
