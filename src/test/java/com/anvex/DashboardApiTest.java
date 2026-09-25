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
import com.anvex.protection.ProtectionConfig;
import com.anvex.protection.ProtectionEngine;
import com.anvex.server.RunManager;
import com.anvex.util.AppConfig;
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

        Supplier<String> testToken = () -> null;
        api = new DashboardApiServer(detection, tracker, protection, detectionConfig, protectionConfig,
                history, runs, metrics, bus, testToken);
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
    void tokenRequiredWhenConfigured() throws Exception {
        DatabaseManager.resetInstance();
        tearDown();
        setUpWithToken("test-token-123");

        HttpConnection denied = get("/api/status");
        assertEquals(401, denied.status());

        HttpConnection allowed = getWithBearer("/api/status", "test-token-123");
        assertEquals(200, allowed.status());
        assertTrue(allowed.body().contains("\"runId\""));
    }

    private void setUpWithToken(String token) throws Exception {
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

        Supplier<String> testToken = () -> token;
        api = new DashboardApiServer(detection, tracker, protection, detectionConfig, protectionConfig,
                history, runs, metrics, bus, testToken);
        api.start();
        port = AppConfig.API_PORT;
    }

    private HttpConnection getWithBearer(String path, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://127.0.0.1:" + port + path)
                .toURL().openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        return new HttpConnection(connection);
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
