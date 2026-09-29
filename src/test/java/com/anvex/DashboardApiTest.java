package com.anvex;

import com.anvex.api.DashboardApiServer;
import com.anvex.detection.DetectionConfig;
import com.anvex.detection.DetectionEngine;
import com.anvex.detection.SourceActivityTracker;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.monitoring.MetricsCollector;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.EventRepository;
import com.anvex.persistence.UserRepository;
import com.anvex.protection.ProtectionConfig;
import com.anvex.protection.ProtectionEngine;
import com.anvex.protection.ProtectionPhase;
import com.anvex.server.LoginProcessor;
import com.anvex.server.RunManager;
import com.anvex.util.AppConfig;
import com.anvex.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class DashboardApiTest {

    private DatabaseManager db;
    private EventBus bus;
    private DashboardApiServer api;
    private int port;
    private Supplier<String> tokenSupplier = () -> null;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager.resetInstance();
        db = DatabaseManager.getTestInstance();
        db.initialize();
        bus = new EventBus();

        DetectionConfig detectionConfig = new DetectionConfig();
        ProtectionConfig protectionConfig = new ProtectionConfig();
        SourceActivityTracker tracker = new SourceActivityTracker();
        DetectionEngine detection = new DetectionEngine(detectionConfig, tracker, bus);
        RunManager runs = new RunManager(db, bus);
        ProtectionEngine protection = new ProtectionEngine(detection, protectionConfig, bus, runs);
        EventRepository history = new EventRepository(db);
        MetricsCollector metrics = new MetricsCollector();
        bus.subscribe(detection);
        bus.subscribe(protection);
        bus.subscribe(history);
        bus.subscribe(metrics::onEvent);

        LoginProcessor loginProcessor = new LoginProcessor(new UserRepository(db), bus, runs);
        api = new DashboardApiServer(detection, tracker, protection, detectionConfig, protectionConfig,
                history, runs, metrics, bus, AppConfig.API_PORT, tokenSupplier, loginProcessor);
        api.start();
        port = AppConfig.API_PORT;
        runs.startRun("api-test");
    }

    @AfterEach
    void tearDown() {
        api.stop();
        bus.shutdown();
        DatabaseManager.resetInstance();
    }

    private HttpConnection get(String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + path)
                .toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        return new HttpConnection(connection);
    }

    private HttpConnection request(String method, String path, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + path)
                .toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod(method);
        if (body != null) {
            connection.setDoOutput(true);
            connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        }
        return new HttpConnection(connection);
    }

    private static final class HttpConnection {
        private final HttpURLConnection connection;
        private Integer status;
        private String body;

        HttpConnection(HttpURLConnection connection) {
            this.connection = connection;
        }

        int status() throws Exception {
            if (status == null) {
                status = connection.getResponseCode();
            }
            return status;
        }

        String body() throws Exception {
            if (body == null) {
                status();
                InputStream stream = status < 400
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                if (stream == null) {
                    body = "";
                } else {
                    StringBuilder out = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            out.append(line);
                        }
                    }
                    body = out.toString();
                }
            }
            return body;
        }
    }

    @Test
    void statusEndpointReportsRunAndSources() throws Exception {
        HttpConnection response = get("/api/status");
        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"runId\""));
        assertTrue(response.body().contains("\"trackedSources\""));
    }

    @Test
    void emptySourcesListInitially() throws Exception {
        HttpConnection response = get("/api/sources");
        assertEquals(200, response.status());
        assertEquals("[]", response.body().trim());
    }

    @Test
    void configGetAndPutWork() throws Exception {
        HttpConnection initial = get("/api/config");
        assertEquals(200, initial.status());
        assertTrue(initial.body().contains("\"highDelayMs\":3000"));

        HttpConnection updated = request("PUT", "/api/config", "{\"highDelayMs\": 250}");
        assertEquals(200, updated.status());
        assertTrue(updated.body().contains("\"highDelayMs\":250"));

        HttpConnection invalid = request("PUT", "/api/config", "{\"highDelayMs\": -5}");
        assertEquals(400, invalid.status());
    }

    @Test
    void unknownEndpointReturns404() throws Exception {
        HttpConnection response = get("/api/unknown");
        assertEquals(404, response.status());
    }

    @Test
    void simulateEndpointProcessesLoginThroughSharedPipeline() throws Exception {
        UserRepository users = new UserRepository(db);
        users.createUser("sim_user", PasswordUtil.hashPassword("pass123"), "LEGITIMATE");

        HttpConnection response = request("POST", "/api/simulate",
                "{\"username\":\"sim_user\",\"password\":\"pass123\",\"clientType\":\"LEGITIMATE\",\"sourceId\":\"192.168.1.77\"}");
        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"outcome\":\"SUCCESS\""), response.body());

        assertTrue(bus.awaitIdle(3000), "Event should flow through the pipeline");

        HttpConnection sources = get("/api/sources");
        assertTrue(sources.body().contains("192.168.1.77"),
                "The simulated attempt must be visible through the same backend state");

        HttpConnection events = get("/api/events?after=0&limit=100");
        assertTrue(events.body().contains("LOGIN_SUCCESS"),
                "The simulated attempt must be persisted by the shared pipeline");
    }

    @Test
    void simulateEndpointReturnsBlockedWhenSourceUnderProtection() throws Exception {
        bus.publish(SecurityEvent.builder()
                .eventType(SecurityEventType.THREAT_ESCALATED)
                .source("10.9.9.9")
                .outcome("CRITICAL")
                .message("test escalation")
                .threatLevel(com.anvex.event.ThreatLevel.CRITICAL)
                .build());
        assertTrue(bus.awaitIdle(3000), "Escalation should be processed");

        HttpConnection response = request("POST", "/api/simulate",
                "{\"username\":\"lab_target\",\"password\":\"whatever\",\"clientType\":\"ATTACKER\",\"sourceId\":\"10.9.9.9\"}");
        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"outcome\":\"BLOCKED\""), response.body());

        assertTrue(bus.awaitIdle(3000), "Blocked event should be processed");
        HttpConnection events = get("/api/events?after=0&limit=100");
        assertTrue(events.body().contains("LOGIN_BLOCKED"),
                "The blocked attempt must flow through the same pipeline");
    }

    @Test
    void simulateEndpointRejectsInvalidBody() throws Exception {
        HttpConnection missing = request("POST", "/api/simulate", "{\"username\":\"only\"}");
        assertEquals(400, missing.status());

        HttpConnection badType = request("POST", "/api/simulate",
                "{\"username\":\"u\",\"password\":\"p\",\"clientType\":\"SOMETHING\",\"sourceId\":\"1.2.3.4\"}");
        assertEquals(400, badType.status());
    }

    @Test
    void runsEndpointStartsRun() throws Exception {
        HttpConnection response = request("POST", "/api/runs", "{\"label\":\"Browser Run\"}");
        assertEquals(200, response.status());
        assertTrue(response.body().contains("\"runId\""), response.body());
        assertTrue(response.body().contains("Browser Run"), response.body());

        assertTrue(bus.awaitIdle(3000), "Run event should be processed");
        HttpConnection list = get("/api/runs");
        assertTrue(list.body().contains("Browser Run"), "Started run must appear in run history");
    }

    @Test
    void runsEndEndpointCompletesRun() throws Exception {
        request("POST", "/api/runs", "{\"label\":\"End Test\"}");
        assertTrue(bus.awaitIdle(3000));

        HttpConnection ended = request("POST", "/api/runs/end", null);
        assertEquals(200, ended.status());
        assertTrue(ended.body().contains("\"status\":\"COMPLETED\""), ended.body());

        assertTrue(bus.awaitIdle(3000));
        HttpConnection list = get("/api/runs");
        assertTrue(list.body().contains("COMPLETED"), "Ended run must show COMPLETED in history");

        HttpConnection noRun = request("POST", "/api/runs/end", null);
        assertEquals(400, noRun.status(), "Ending with no active run must fail");
    }

    @Test
    void statusEndpointReportsEventCounts() throws Exception {
        request("POST", "/api/simulate",
                "{\"username\":\"nobody\",\"password\":\"wrong\",\"clientType\":\"ATTACKER\",\"sourceId\":\"10.5.5.5\"}");
        assertTrue(bus.awaitIdle(3000));

        HttpConnection response = get("/api/status");
        assertTrue(response.body().contains("\"eventCount\":"), response.body());
        assertFalse(response.body().contains("\"eventCount\":0"),
                "Event count must reflect persisted events after the simulation");
    }

    @Test
    void tokenRequiredWhenConfigured() throws Exception {
        tearDown();
        tokenSupplier = () -> "test-token-123";
        setUp();

        HttpConnection denied = get("/api/status");
        assertEquals(401, denied.status());

        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/api/status")
                .toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Authorization", "Bearer test-token-123");
        HttpConnection allowed = new HttpConnection(connection);
        assertEquals(200, allowed.status());
        assertTrue(allowed.body().contains("\"runId\""));
    }

    @Test
    void sseStreamPushesLiveEvents() throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + "/api/events/stream")
                .toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "text/event-stream");

        StringBuilder received = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    synchronized (received) {
                        received.append(line).append("\n");
                    }
                    if (line.startsWith("data:") && line.contains("LOGIN_FAILURE")) {
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        });
        reader.setDaemon(true);
        reader.start();

        Thread.sleep(500);
        bus.publish(SecurityEvent.builder()
                .runId(1)
                .eventType(SecurityEventType.LOGIN_FAILURE)
                .username("target_user")
                .clientType("ATTACKER")
                .source("192.168.1.50")
                .outcome("FAILURE")
                .message("sse test")
                .build());
        assertTrue(bus.awaitIdle(3000), "Event should be processed");

        reader.join(5000);
        synchronized (received) {
            assertTrue(received.toString().contains("retry:"), "SSE stream should open with a retry hint");
            assertTrue(received.toString().contains("LOGIN_FAILURE"), "SSE stream should push the live event");
        }
        connection.disconnect();
    }
}
