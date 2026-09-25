package com.anvex;

import com.anvex.event.CanonicalEventLog;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.monitoring.MetricsCollector;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class EventFoundationTest {

    @Test
    void eventBusGeneratesMonotonicSequenceNumbers() throws InterruptedException {
        EventBus bus = new EventBus();
        AtomicLong lastSeq = new AtomicLong(0);

        bus.subscribe(event -> {
            long seq = event.getSequenceNumber();
            assertTrue(seq > lastSeq.getAndSet(seq), "Sequence numbers must be monotonic");
        });

        for (int i = 0; i < 100; i++) {
            bus.publish(testEvent(i));
        }
        assertTrue(bus.awaitIdle(2000), "All events should be processed");
        assertEquals(100, bus.getCurrentSequence());
    }

    @Test
    void eventBusDeliversToMultipleListeners() throws InterruptedException {
        EventBus bus = new EventBus();
        AtomicLong listener1Count = new AtomicLong(0);
        AtomicLong listener2Count = new AtomicLong(0);

        bus.subscribe(e -> listener1Count.incrementAndGet());
        bus.subscribe(e -> listener2Count.incrementAndGet());

        for (int i = 0; i < 50; i++) {
            bus.publish(testEvent(i));
        }
        assertTrue(bus.awaitIdle(2000), "All events should be processed");

        assertEquals(50, listener1Count.get());
        assertEquals(50, listener2Count.get());
    }

    @Test
    void canonicalEventLogStoresEventsByRunId() throws InterruptedException {
        CanonicalEventLog log = new CanonicalEventLog();
        EventBus bus = new EventBus();
        bus.subscribe(log::record);

        log.setCurrentRunId(1);
        bus.publish(SecurityEvent.builder()
                .runId(1).eventType(SecurityEventType.LOGIN_FAILURE).username("lab_target")
                .clientType("ATTACKER").outcome("FAILURE").message("test").build());
        bus.publish(SecurityEvent.builder()
                .runId(1).eventType(SecurityEventType.LOGIN_SUCCESS).username("lab_target")
                .clientType("ATTACKER").outcome("SUCCESS").message("test").build());

        log.setCurrentRunId(2);
        bus.publish(SecurityEvent.builder()
                .runId(2).eventType(SecurityEventType.LOGIN_FAILURE).username("lab_target")
                .clientType("ATTACKER").outcome("FAILURE").message("test").build());

        assertTrue(bus.awaitIdle(2000), "All events should be processed");
        assertEquals(2, log.getEventsForRun(1).size());
        assertEquals(1, log.getEventsForRun(2).size());
    }

    @Test
    void metricsCollectorTracksAttackerMetrics() throws InterruptedException {
        MetricsCollector metrics = new MetricsCollector();
        EventBus bus = new EventBus();
        bus.subscribe(metrics::onEvent);

        long runId = 1;
        bus.publish(SecurityEvent.builder().runId(runId).eventType(SecurityEventType.ATTACK_STARTED).build());
        bus.publish(SecurityEvent.builder().runId(runId).eventType(SecurityEventType.LOGIN_FAILURE)
                .username("lab_target").clientType("ATTACKER").outcome("FAILURE").message("fail").build());
        bus.publish(SecurityEvent.builder().runId(runId).eventType(SecurityEventType.LOGIN_FAILURE)
                .username("lab_target").clientType("ATTACKER").outcome("FAILURE").message("fail").build());
        bus.publish(SecurityEvent.builder().runId(runId).eventType(SecurityEventType.LOGIN_SUCCESS)
                .username("lab_target").clientType("ATTACKER").outcome("SUCCESS").message("success").build());
        bus.publish(SecurityEvent.builder().runId(runId).eventType(SecurityEventType.ATTACK_COMPLETED).build());

        assertTrue(bus.awaitIdle(2000), "All events should be processed");
        MetricsCollector.MetricsSnapshot snap = metrics.snapshot();
        assertEquals(3, snap.totalAttackerAttempts());
        assertEquals(2, snap.attackerFailures());
        assertEquals(1, snap.attackerSuccesses());
        assertTrue(snap.accountCompromised());
    }

    @Test
    void metricsCollectorTracksLegitimateUsersSeparately() throws InterruptedException {
        MetricsCollector metrics = new MetricsCollector();
        EventBus bus = new EventBus();
        bus.subscribe(metrics::onEvent);

        bus.publish(SecurityEvent.builder().runId(1).eventType(SecurityEventType.LOGIN_SUCCESS)
                .username("legit_user_1").clientType("LEGITIMATE").outcome("SUCCESS").message("ok").build());
        bus.publish(SecurityEvent.builder().runId(1).eventType(SecurityEventType.LOGIN_FAILURE)
                .username("legit_user_1").clientType("LEGITIMATE").outcome("FAILURE").message("fail").build());

        assertTrue(bus.awaitIdle(2000), "All events should be processed");
        MetricsCollector.MetricsSnapshot snap = metrics.snapshot();
        assertEquals(1, snap.legitimateSuccesses());
        assertEquals(1, snap.legitimateFailures());
        assertEquals(0, snap.totalAttackerAttempts());
    }

    @Test
    void concurrentEventPublishingPreservesOrdering() throws InterruptedException {
        EventBus bus = new EventBus();
        CanonicalEventLog log = new CanonicalEventLog();
        bus.subscribe(log::record);

        int threads = 10;
        int eventsPerThread = 100;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            executor.submit(() -> {
                for (int i = 0; i < eventsPerThread; i++) {
                    bus.publish(SecurityEvent.builder()
                            .runId(1)
                            .eventType(SecurityEventType.LOGIN_FAILURE)
                            .username("lab_target")
                            .clientType("ATTACKER")
                            .outcome("FAILURE")
                            .message("thread-" + threadId + "-" + i)
                            .build());
                }
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();
        assertTrue(bus.awaitIdle(5000), "All events should be processed");

        assertEquals(threads * eventsPerThread, log.getEventsForRun(1).size());
        assertEquals(threads * eventsPerThread, bus.getCurrentSequence());
    }

    @Test
    void metricsResetClearsAllCounters() throws InterruptedException {
        MetricsCollector metrics = new MetricsCollector();
        EventBus bus = new EventBus();
        bus.subscribe(metrics::onEvent);

        bus.publish(SecurityEvent.builder().runId(1).eventType(SecurityEventType.ATTACK_STARTED).build());
        bus.publish(SecurityEvent.builder().runId(1).eventType(SecurityEventType.LOGIN_FAILURE)
                .username("lab_target").clientType("ATTACKER").outcome("FAILURE").message("fail").build());
        assertTrue(bus.awaitIdle(2000), "All events should be processed");

        metrics.reset();

        MetricsCollector.MetricsSnapshot snap = metrics.snapshot();
        assertEquals(0, snap.totalAttackerAttempts());
        assertEquals(0, snap.attackerFailures());
        assertFalse(snap.accountCompromised());
    }

    private SecurityEvent testEvent(int i) {
        return SecurityEvent.builder()
                .runId(1)
                .eventType(SecurityEventType.LOGIN_FAILURE)
                .username("test")
                .clientType("ATTACKER")
                .outcome("FAILURE")
                .message("test-" + i)
                .timestamp(Instant.now())
                .build();
    }
}