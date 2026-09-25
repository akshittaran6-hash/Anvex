package com.anvex.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class EventBus {

    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);

    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();
    private final LinkedBlockingQueue<SecurityEvent> queue = new LinkedBlockingQueue<>();
    private final AtomicInteger pendingEvents = new AtomicInteger(0);
    private final AtomicLong sequenceCounter;
    private final Thread dispatcherThread;
    private volatile boolean running = true;

    public EventBus() {
        this(0);
    }

    public EventBus(long initialSequence) {
        sequenceCounter = new AtomicLong(initialSequence);
        dispatcherThread = new Thread(this::drainQueue, "EventBus-Dispatcher");
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
    }

    public void subscribe(EventListener listener) {
        listeners.add(listener);
        logger.debug("Subscribed listener: {}", listener.getClass().getSimpleName());
    }

    public void unsubscribe(EventListener listener) {
        listeners.remove(listener);
    }

    public void publish(SecurityEvent event) {
        if (!running && Thread.currentThread() != dispatcherThread) {
            throw new IllegalStateException("EventBus is shut down");
        }
        pendingEvents.incrementAndGet();
        queue.offer(event);
        logger.trace("Event enqueued: {}", event);
    }

    private void drainQueue() {
        while (running || !queue.isEmpty()) {
            try {
                SecurityEvent event = queue.poll(50, TimeUnit.MILLISECONDS);
                if (event != null) {
                    try {
                        SecurityEvent sequencedEvent = assignSequence(event);
                        dispatch(sequencedEvent);
                    } finally {
                        pendingEvents.decrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        logger.debug("EventBus dispatcher stopped");
    }

    private SecurityEvent assignSequence(SecurityEvent event) {
        long seq = sequenceCounter.incrementAndGet();
        if (event.getSequenceNumber() == seq) {
            return event;
        }
        return SecurityEvent.builder()
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
                .threatLevel(event.getThreatLevel())
                .threatScore(event.getThreatScore())
                .evidence(event.getEvidence())
                .build();
    }

    private void dispatch(SecurityEvent event) {
        for (EventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                logger.error("Listener {} threw exception", listener.getClass().getSimpleName(), e);
            }
        }
    }

    public boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (pendingEvents.get() > 0) {
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            Thread.sleep(5);
        }
        return true;
    }

    public long getCurrentSequence() {
        return sequenceCounter.get();
    }

    public int getPendingCount() {
        return pendingEvents.get();
    }

    public void shutdown() {
        running = false;
        try {
            dispatcherThread.join(30_000);
            if (dispatcherThread.isAlive()) logger.error("EventBus shutdown timed out with {} events pending", pendingEvents.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
