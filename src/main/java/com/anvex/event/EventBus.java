package com.anvex.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class EventBus {

    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);

    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(EventListener listener) {
        listeners.add(listener);
        logger.debug("Subscribed listener: {}", listener.getClass().getSimpleName());
    }

    public void unsubscribe(EventListener listener) {
        listeners.remove(listener);
    }

    public void publish(SecurityEvent event) {
        long seq = sequenceGenerator.incrementAndGet();
        SecurityEvent sequencedEvent = SecurityEvent.builder()
                .sequenceNumber(seq)
                .eventId(event.getEventId())
                .runId(event.getRunId())
                .timestamp(event.getTimestamp())
                .eventType(event.getEventType())
                .username(event.getUsername())
                .clientType(event.getClientType())
                .source(event.getSource())
                .outcome(event.getOutcome())
                .message(event.getMessage())
                .metadata(event.getMetadata())
                .build();

        for (EventListener listener : listeners) {
            try {
                listener.onEvent(sequencedEvent);
            } catch (Exception e) {
                logger.error("Listener {} threw exception", listener.getClass().getSimpleName(), e);
            }
        }
    }

    public long getCurrentSequence() {
        return sequenceGenerator.get();
    }

    public void reset() {
        sequenceGenerator.set(0);
    }
}