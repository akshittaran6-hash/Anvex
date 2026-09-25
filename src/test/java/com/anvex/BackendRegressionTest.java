package com.anvex;

import com.anvex.detection.*;
import com.anvex.event.*;
import com.anvex.persistence.*;
import com.anvex.protection.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.time.Instant;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class BackendRegressionTest {
    private static SecurityEvent attempt(String source, Instant at, SecurityEventType type) {
        return SecurityEvent.builder().runId(17).source(source).timestamp(at).eventType(type)
                .outcome(type == SecurityEventType.LOGIN_BLOCKED ? "BLOCKED" : "FAILURE").build();
    }

    @Test void persistentHistorySurvivesInitialize() throws Exception {
        DatabaseManager.resetInstance();
        DatabaseManager db=DatabaseManager.getTestInstance(); db.initialize();
        EventRepository events=new EventRepository(db);
        EventBus bus=new EventBus(); bus.subscribe(events);
        bus.publish(attempt("history", Instant.now(), SecurityEventType.LOGIN_FAILURE));
        assertTrue(bus.awaitIdle(2000)); assertEquals(1,events.countEvents());
        db.initialize();
        assertEquals(1,events.countEvents());
        bus.shutdown(); DatabaseManager.resetInstance();
    }

    @Test void fileHistorySurvivesNewDatabaseManager(@TempDir Path dir) throws Exception {
        String path=dir.resolve("history").toAbsolutePath().toString().replace('\\','/');
        DatabaseManager.resetInstance();
        DatabaseManager first=DatabaseManager.getFileInstance(path);first.initialize();
        EventRepository repository=new EventRepository(first);
        repository.onEvent(attempt("restart-source",Instant.now(),SecurityEventType.LOGIN_FAILURE));
        DatabaseManager.resetInstance();
        DatabaseManager second=DatabaseManager.getFileInstance(path);second.initialize();
        assertEquals(1,new EventRepository(second).findBySource("restart-source").size());
        DatabaseManager.resetInstance();
    }

    @Test void inactiveScoreCommitsAndReleaseKeepsOriginalRun() throws Exception {
        EventBus bus=new EventBus(); DetectionConfig cfg=new DetectionConfig();
        SourceActivityTracker tracker=new SourceActivityTracker();
        DetectionEngine detection=new DetectionEngine(cfg,tracker,bus);
        ProtectionConfig protectionConfig=new ProtectionConfig();
        ProtectionEngine protection=new ProtectionEngine(detection,protectionConfig,bus);
        List<SecurityEvent> received=new CopyOnWriteArrayList<>();
        bus.subscribe(detection);bus.subscribe(protection);bus.subscribe(received::add);
        Instant base=Instant.now();
        for(int i=0;i<20;i++)bus.publish(attempt("source",base.plusMillis(i*100),SecurityEventType.LOGIN_FAILURE));
        assertTrue(bus.awaitIdle(5000));assertEquals(ThreatLevel.CRITICAL,detection.getCurrentLevel("source"));
        assertEquals(ProtectionPhase.BLOCKED,protection.getPhase("source"));
        assertEquals(ProtectionAction.ALLOW,protection.evaluateRequest("source",base.plusSeconds(120)));
        assertTrue(bus.awaitIdle(5000));
        assertEquals(ThreatLevel.NORMAL,detection.getCurrentLevel("source"));
        assertEquals(0,detection.getStoredScore("source"));
        assertTrue(received.stream().anyMatch(e->e.getEventType()==SecurityEventType.SOURCE_RELEASED && e.getRunId()==17));
        assertTrue(received.stream().anyMatch(e->e.getEventType()==SecurityEventType.THREAT_DEESCALATED && e.getRunId()==17));
        bus.shutdown();
    }

    @Test void continuedBlockedTrafficGetsBlockedAgainAfterCooldown() throws Exception {
        EventBus bus=new EventBus(); DetectionEngine detection=new DetectionEngine(new DetectionConfig(),new SourceActivityTracker(),bus);
        ProtectionEngine protection=new ProtectionEngine(detection,new ProtectionConfig(),bus);
        List<SecurityEvent> received=new CopyOnWriteArrayList<>();
        bus.subscribe(detection);bus.subscribe(protection);bus.subscribe(received::add);
        Instant base=Instant.now();
        for(int i=0;i<20;i++)bus.publish(attempt("persistent",base.plusMillis(i*100),SecurityEventType.LOGIN_FAILURE));
        assertTrue(bus.awaitIdle(5000));
        for(int i=2;i<62;i++)bus.publish(attempt("persistent",base.plusSeconds(i),SecurityEventType.LOGIN_BLOCKED));
        assertTrue(bus.awaitIdle(5000));
        assertEquals(ProtectionAction.BLOCK,protection.evaluateRequest("persistent",base.plusSeconds(62)));
        assertTrue(bus.awaitIdle(5000));
        assertTrue(received.stream().filter(e->e.getEventType()==SecurityEventType.SOURCE_BLOCKED).count()>=2);
        bus.shutdown();
    }

    @Test void concurrentSequenceNumbersMatchDeliveryOrder() throws Exception {
        EventBus bus=new EventBus();List<Long> seq=new CopyOnWriteArrayList<>();bus.subscribe(e->seq.add(e.getSequenceNumber()));
        ExecutorService pool=Executors.newFixedThreadPool(12);CountDownLatch gate=new CountDownLatch(1);
        for(int t=0;t<12;t++)pool.submit(()->{try{gate.await();for(int i=0;i<300;i++)bus.publish(attempt("x",Instant.now(),SecurityEventType.LOGIN_FAILURE));}
            catch(InterruptedException e){Thread.currentThread().interrupt();}});
        gate.countDown();pool.shutdown();assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS));
        assertTrue(bus.awaitIdle(10000));assertEquals(3600,seq.size());
        for(int i=0;i<seq.size();i++)assertEquals(i+1L,seq.get(i).longValue());
        bus.shutdown();
    }
}
