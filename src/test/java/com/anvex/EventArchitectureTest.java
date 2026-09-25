package com.anvex;

import com.anvex.event.CanonicalEventLog;
import com.anvex.event.Evidence;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.event.ThreatLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class EventArchitectureTest {

    @Test
    void threatLevelHasFourOrderedLevels() {
        assertEquals(4, ThreatLevel.values().length);
        assertTrue(ThreatLevel.NORMAL.getSeverity() < ThreatLevel.SUSPICIOUS.getSeverity());
        assertTrue(ThreatLevel.SUSPICIOUS.getSeverity() < ThreatLevel.HIGH.getSeverity());
        assertTrue(ThreatLevel.HIGH.getSeverity() < ThreatLevel.CRITICAL.getSeverity());
    }

    @Test
    void threatLevelEscalationCheckWorks() {
        assertTrue(ThreatLevel.HIGH.isAtLeast(ThreatLevel.SUSPICIOUS));
        assertTrue(ThreatLevel.CRITICAL.isAtLeast(ThreatLevel.CRITICAL));
        assertFalse(ThreatLevel.NORMAL.isAtLeast(ThreatLevel.SUSPICIOUS));
        assertFalse(ThreatLevel.SUSPICIOUS.isAtLeast(ThreatLevel.HIGH));
    }

    @Test
    void evidenceDescribesCoreThreePoints() {
        Evidence evidence = Evidence.builder()
                .failedAttemptCount(25)
                .timeWindowMs(30_000)
                .requestFrequency(0.83)
                .thresholdExceeded(true)
                .build();

        List<String> lines = evidence.describe();
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("25"), "First bullet must state the failed attempt count");
        assertTrue(lines.get(1).contains("30.0"), "Second bullet must state the time window");
        assertTrue(lines.get(1).contains("attempts/second"), "Second bullet must state the frequency");
        assertTrue(lines.get(2).contains("threshold"), "Third bullet must state threshold exceedance");
    }

    @Test
    void evidenceWithoutWindowOmitsFrequencyLine() {
        Evidence evidence = Evidence.builder()
                .failedAttemptCount(3)
                .thresholdExceeded(false)
                .build();

        List<String> lines = evidence.describe();
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).contains("3"));
    }

    @Test
    void securityEventCarriesThreatLevelAndEvidence() {
        Evidence evidence = Evidence.builder()
                .failedAttemptCount(10)
                .timeWindowMs(5_000)
                .requestFrequency(2.0)
                .thresholdExceeded(true)
                .build();

        SecurityEvent event = SecurityEvent.builder()
                .runId(1)
                .eventType(SecurityEventType.SOURCE_BLOCKED)
                .username("lab_target")
                .clientType("ATTACKER")
                .source("192.168.1.50")
                .outcome("BLOCKED")
                .message("Source temporarily blocked")
                .threatLevel(ThreatLevel.CRITICAL)
                .evidence(evidence)
                .build();

        assertEquals(ThreatLevel.CRITICAL, event.getThreatLevel());
        assertEquals(evidence, event.getEvidence());
        assertEquals("192.168.1.50", event.getSource());
        assertEquals(SecurityEventType.SOURCE_BLOCKED, event.getEventType());
    }

    @Test
    void securityEventDefaultsToNullThreatContext() {
        SecurityEvent event = SecurityEvent.builder()
                .runId(1)
                .eventType(SecurityEventType.LOGIN_SUCCESS)
                .username("legit_user")
                .clientType("LEGITIMATE")
                .outcome("SUCCESS")
                .message("ok")
                .build();

        assertNull(event.getThreatLevel());
        assertNull(event.getEvidence());
    }

    @Test
    void eventBusTransportsThreatContextThroughQueue() throws InterruptedException {
        EventBus bus = new EventBus();
        List<SecurityEvent> received = new CopyOnWriteArrayList<>();
        bus.subscribe(received::add);

        Evidence evidence = Evidence.builder()
                .failedAttemptCount(15)
                .timeWindowMs(30_000)
                .requestFrequency(0.5)
                .thresholdExceeded(true)
                .build();

        bus.publish(SecurityEvent.builder()
                .runId(1)
                .eventType(SecurityEventType.PROTECTION_ENABLED)
                .source("192.168.1.50")
                .outcome("PROTECTED")
                .message("Protection enabled")
                .threatLevel(ThreatLevel.HIGH)
                .evidence(evidence)
                .build());

        assertTrue(bus.awaitIdle(2000), "Event should be processed");
        assertEquals(1, received.size());
        assertEquals(ThreatLevel.HIGH, received.get(0).getThreatLevel());
        assertEquals(evidence, received.get(0).getEvidence());
    }

    @Test
    void eventBusPublishDoesNotBlockWhileListenerIsProcessing() throws InterruptedException {
        EventBus bus = new EventBus();
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch releaseListener = new CountDownLatch(1);

        bus.subscribe(event -> {
            received.countDown();
            try {
                releaseListener.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        bus.publish(testEvent(1));

        assertTrue(received.await(2, TimeUnit.SECONDS), "Event should be delivered asynchronously");
        assertEquals(1, releaseListener.getCount(), "publish() must return while the listener is still processing");

        releaseListener.countDown();
        assertTrue(bus.awaitIdle(2000), "Queue should drain after listener completes");
    }

    @Test
    void listenerExceptionDoesNotStopOtherListeners() throws InterruptedException {
        EventBus bus = new EventBus();
        AtomicLong goodListenerCount = new AtomicLong(0);

        bus.subscribe(event -> { throw new RuntimeException("boom"); });
        bus.subscribe(event -> goodListenerCount.incrementAndGet());

        for (int i = 0; i < 10; i++) {
            bus.publish(testEvent(i));
        }
        assertTrue(bus.awaitIdle(2000), "All events should be processed");
        assertEquals(10, goodListenerCount.get());
    }

    @Test
    void awaitIdleTimesOutWhenQueueIsNotDrained() throws InterruptedException {
        EventBus bus = new EventBus();
        CountDownLatch releaseListener = new CountDownLatch(1);

        bus.subscribe(event -> {
            try {
                releaseListener.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        bus.publish(testEvent(1));
        assertFalse(bus.awaitIdle(200), "awaitIdle should time out while the listener is blocked");

        releaseListener.countDown();
        assertTrue(bus.awaitIdle(2000), "Queue should drain after release");
    }

    @Test
    void shutdownDrainsRemainingEvents() throws InterruptedException {
        EventBus bus = new EventBus();
        AtomicLong count = new AtomicLong(0);
        bus.subscribe(event -> count.incrementAndGet());

        for (int i = 0; i < 100; i++) {
            bus.publish(testEvent(i));
        }
        bus.shutdown();

        assertEquals(100, count.get(), "shutdown must drain all queued events");
        assertTrue(bus.getPendingCount() == 0, "No events should remain pending after shutdown");
    }

    @Test
    void eventBusPreservesFifoOrderingThroughQueue() throws InterruptedException {
        EventBus bus = new EventBus();
        CanonicalEventLog log = new CanonicalEventLog();
        AtomicLong lastSeq = new AtomicLong(0);

        bus.subscribe(event -> {
            long seq = event.getSequenceNumber();
            assertTrue(seq > lastSeq.getAndSet(seq), "FIFO order must be preserved by the dispatcher");
        });
        bus.subscribe(log::record);

        for (int i = 0; i < 500; i++) {
            bus.publish(testEvent(i));
        }
        assertTrue(bus.awaitIdle(5000), "All events should be processed");
        assertEquals(500, log.size());
    }

    private SecurityEvent testEvent(int i) {
        return SecurityEvent.builder()
                .runId(1)
                .eventType(SecurityEventType.LOGIN_FAILURE)
                .username("test")
                .clientType("ATTACKER")
                .outcome("FAILURE")
                .message("test-" + i)
                .build();
    }
}
