package com.anvex.api;

import com.anvex.detection.DetectionConfig;
import com.anvex.detection.DetectionEngine;
import com.anvex.detection.SourceActivityTracker;
import com.anvex.detection.ThreatAssessment;
import com.anvex.event.Evidence;
import com.anvex.event.EventBus;
import com.anvex.event.EventListener;
import com.anvex.event.SecurityEvent;
import com.anvex.monitoring.MetricsCollector;
import com.anvex.persistence.EventRepository;
import com.anvex.persistence.EventRepository.PersistedEvent;
import com.anvex.protection.ProtectionConfig;
import com.anvex.protection.ProtectionEngine;
import com.anvex.server.RunManager;
import com.anvex.server.RunManager.RunInfo;
import com.anvex.util.AppConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public final class DashboardApiServer {

    private static final Logger logger = LoggerFactory.getLogger(DashboardApiServer.class);

    private final DetectionEngine detection;
    private final SourceActivityTracker tracker;
    private final ProtectionEngine protection;
    private final DetectionConfig detectionConfig;
    private final ProtectionConfig protectionConfig;
    private final EventRepository events;
    private final RunManager runs;
    private final MetricsCollector metrics;
    private final EventBus bus;
    private final Supplier<String> tokenSupplier;
    private final int apiPort;
    private final List<SseClient> sseClients = new CopyOnWriteArrayList<>();
    private final EventListener sseListener = this::broadcastEvent;
    private final ExecutorService sseWriter;
    private HttpServer httpServer;

    public DashboardApiServer(DetectionEngine detection, SourceActivityTracker tracker, ProtectionEngine protection,
                              DetectionConfig detectionConfig, ProtectionConfig protectionConfig,
                              EventRepository events, RunManager runs, MetricsCollector metrics, EventBus bus) {
        this(detection, tracker, protection, detectionConfig, protectionConfig, events, runs, metrics, bus,
                AppConfig.API_PORT, () -> System.getenv("ANVEX_API_TOKEN"));
    }

    public DashboardApiServer(DetectionEngine detection, SourceActivityTracker tracker, ProtectionEngine protection,
                              DetectionConfig detectionConfig, ProtectionConfig protectionConfig,
                              EventRepository events, RunManager runs, MetricsCollector metrics, EventBus bus,
                              int apiPort) {
        this(detection, tracker, protection, detectionConfig, protectionConfig, events, runs, metrics, bus,
                apiPort, () -> System.getenv("ANVEX_API_TOKEN"));
    }

    public DashboardApiServer(DetectionEngine detection, SourceActivityTracker tracker, ProtectionEngine protection,
                              DetectionConfig detectionConfig, ProtectionConfig protectionConfig,
                              EventRepository events, RunManager runs, MetricsCollector metrics, EventBus bus,
                              Supplier<String> tokenSupplier) {
        this(detection, tracker, protection, detectionConfig, protectionConfig, events, runs, metrics, bus,
                AppConfig.API_PORT, tokenSupplier);
    }

    public DashboardApiServer(DetectionEngine detection, SourceActivityTracker tracker, ProtectionEngine protection,
                              DetectionConfig detectionConfig, ProtectionConfig protectionConfig,
                              EventRepository events, RunManager runs, MetricsCollector metrics, EventBus bus,
                              int apiPort, Supplier<String> tokenSupplier) {
        this.detection = detection;
        this.tracker = tracker;
        this.protection = protection;
        this.detectionConfig = detectionConfig;
        this.protectionConfig = protectionConfig;
        this.events = events;
        this.runs = runs;
        this.metrics = metrics;
        this.bus = bus;
        this.apiPort = apiPort;
        this.tokenSupplier = tokenSupplier;

        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "DashboardApi-Sse-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        this.sseWriter = Executors.newSingleThreadExecutor(factory);
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(AppConfig.SERVER_HOST, apiPort), 0);
        httpServer.createContext("/api", this::handleApi);
        httpServer.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "DashboardApi-Http");
            t.setDaemon(true);
            return t;
        }));
        httpServer.start();
        bus.subscribe(sseListener);
        logger.info("Dashboard API listening on {}:{}", AppConfig.SERVER_HOST, apiPort);
    }

    public void stop() {
        bus.unsubscribe(sseListener);
        for (SseClient client : sseClients) {
            client.close();
        }
        sseClients.clear();
        sseWriter.shutdownNow();
        if (httpServer != null) {
            httpServer.stop(0);
        }
        logger.info("Dashboard API stopped");
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        try {
            if (!authorized(exchange)) {
                sendJson(exchange, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            String route = path.length() > 4 ? path.substring(4) : "";

            if ("GET".equals(method) && "/events/stream".equals(route)) {
                handleSse(exchange);
                return;
            }

            switch (method) {
                case "GET" -> handleGet(exchange, route);
                case "PUT" -> handlePut(exchange, route);
                default -> sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, "{\"error\":\"Invalid value\"}");
        } catch (SQLException e) {
            sendJson(exchange, 503, "{\"error\":\"History unavailable\"}");
        } catch (Exception e) {
            logger.error("API error handling {}", exchange.getRequestURI(), e);
            sendJson(exchange, 500, "{\"error\":\"Internal error\"}");
        } finally {
            exchange.close();
        }
    }

    private void handleGet(HttpExchange exchange, String route) throws IOException, SQLException {
        if (route.isEmpty() || route.equals("/")) {
            sendJson(exchange, 200, status());
            return;
        }
        if (route.equals("/status")) {
            sendJson(exchange, 200, status());
            return;
        }
        if (route.equals("/sources")) {
            sendJson(exchange, 200, sources());
            return;
        }
        if (route.equals("/events")) {
            Map<String, String> query = queryParams(exchange);
            long after = Long.parseLong(query.getOrDefault("after", "0"));
            int limit = Math.min(200, Math.max(1, Integer.parseInt(query.getOrDefault("limit", "100"))));
            sendJson(exchange, 200, eventList(events.findAfterId(after, limit)));
            return;
        }
        if (route.equals("/runs")) {
            sendJson(exchange, 200, runs());
            return;
        }
        if (route.equals("/metrics")) {
            sendJson(exchange, 200, metrics());
            return;
        }
        if (route.equals("/config")) {
            sendJson(exchange, 200, config());
            return;
        }

        String[] segments = route.substring(1).split("/");
        if ("sources".equals(segments[0]) && segments.length == 2) {
            sendJson(exchange, 200, source(segments[1]));
            return;
        }
        if ("sources".equals(segments[0]) && segments.length == 3 && "history".equals(segments[2])) {
            sendJson(exchange, 200, sourceHistory(segments[1]));
            return;
        }
        if ("runs".equals(segments[0]) && segments.length == 2) {
            RunInfo run = runs.getRun(Long.parseLong(segments[1]));
            if (run == null) {
                sendJson(exchange, 404, "{\"error\":\"Unknown run\"}");
            } else {
                sendJson(exchange, 200, runJson(run));
            }
            return;
        }
        if ("runs".equals(segments[0]) && segments.length == 3 && "events".equals(segments[2])) {
            sendJson(exchange, 200, eventList(events.findByRun(Long.parseLong(segments[1]))));
            return;
        }
        sendJson(exchange, 404, "{\"error\":\"Unknown endpoint\"}");
    }

    private void handlePut(HttpExchange exchange, String route) throws IOException {
        if (!route.equals("/config")) {
            sendJson(exchange, 404, "{\"error\":\"Unknown endpoint\"}");
            return;
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> settings = JsonParser.parseFlat(body);
        if (settings.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":\"Expected a JSON object of known settings\"}");
            return;
        }
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String error = applySetting(entry.getKey(), entry.getValue());
            if (error != null) {
                sendJson(exchange, 400, "{\"error\":\"" + q(error) + "\"}");
                return;
            }
        }
        refreshAssessments();
        sendJson(exchange, 200, config());
    }

    private void handleSse(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);

        SseClient client = new SseClient(exchange.getResponseBody());
        sseClients.add(client);

        try {
            client.write("retry: 3000\n\n");
        } catch (IOException e) {
            sseClients.remove(client);
            client.close();
            return;
        }

        while (!client.isClosed()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void broadcastEvent(SecurityEvent event) {
        if (sseClients.isEmpty()) {
            return;
        }
        String frame = "data: " + eventJson(event) + "\n\n";
        sseWriter.submit(() -> {
            for (SseClient client : sseClients) {
                try {
                    client.write(frame);
                } catch (IOException e) {
                    sseClients.remove(client);
                    client.close();
                }
            }
        });
    }

    private boolean authorized(HttpExchange exchange) {
        String token = tokenSupplier.get();
        if (token == null || token.isBlank()) {
            return true;
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        return auth != null && auth.equals("Bearer " + token);
    }

    private String status() {
        return "{\"runId\":" + runs.getCurrentRunId()
                + ",\"trackedSources\":" + tracker.sourceIds().size()
                + ",\"serverRunning\":true"
                + ",\"apiPort\":" + apiPort + "}";
    }

    private String source(String id) {
        if (!tracker.sourceIds().contains(id)) {
            return "{\"error\":\"Unknown source\"}";
        }
        ThreatAssessment assessment = detection.assess(id, Instant.now());
        Instant until = protection.getBlockedUntil(id);
        return "{\"source\":" + q(id)
                + ",\"score\":" + assessment.getThreatScore()
                + ",\"level\":" + q(assessment.getThreatLevel().name())
                + ",\"reason\":" + q(assessment.getReason())
                + ",\"phase\":" + q(protection.getPhase(id).name())
                + ",\"blockedUntil\":" + (until == null ? "null" : q(until.toString()))
                + ",\"evidence\":" + evidence(assessment.getEvidence()) + "}";
    }

    private String sources() {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (String id : tracker.sourceIds()) {
            joiner.add(source(id));
        }
        return joiner.toString();
    }

    private String sourceHistory(String source) throws SQLException {
        List<PersistedEvent> history = events.findBySource(source);
        return eventList(history.subList(Math.max(0, history.size() - 200), history.size()));
    }

    private String runs() throws SQLException {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (RunInfo run : runs.listRuns()) {
            joiner.add(runJson(run));
        }
        return joiner.toString();
    }

    private String runJson(RunInfo run) {
        return "{\"runId\":" + run.runId()
                + ",\"label\":" + q(run.label())
                + ",\"status\":" + q(run.status())
                + ",\"startTime\":" + q(run.startTime().toString())
                + ",\"endTime\":" + (run.endTime() == null ? "null" : q(run.endTime().toString())) + "}";
    }

    private String metrics() {
        MetricsCollector.MetricsSnapshot snapshot = metrics.snapshot();
        return "{\"attackerAttempts\":" + snapshot.totalAttackerAttempts()
                + ",\"attackerFailures\":" + snapshot.attackerFailures()
                + ",\"attackerBlocked\":" + snapshot.attackerBlocked()
                + ",\"attackerSuccesses\":" + snapshot.attackerSuccesses()
                + ",\"legitimateSuccesses\":" + snapshot.legitimateSuccesses()
                + ",\"legitimateFailures\":" + snapshot.legitimateFailures()
                + ",\"alerts\":" + snapshot.alertCount()
                + ",\"compromised\":" + snapshot.accountCompromised()
                + ",\"durationMs\":" + snapshot.durationMs() + "}";
    }

    private String eventList(List<PersistedEvent> list) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (PersistedEvent event : list) {
            joiner.add(eventJson(event));
        }
        return joiner.toString();
    }

    private String eventJson(PersistedEvent event) {
        return "{\"id\":" + event.eventId()
                + ",\"sequence\":" + event.sequenceNumber()
                + ",\"runId\":" + event.runId()
                + ",\"timestamp\":" + q(event.timestamp().toString())
                + ",\"type\":" + q(event.eventType())
                + ",\"source\":" + q(event.source())
                + ",\"outcome\":" + q(event.outcome())
                + ",\"level\":" + (event.threatLevel() == null ? "null" : q(event.threatLevel().name()))
                + ",\"score\":" + (event.threatScore() == null ? "null" : event.threatScore())
                + ",\"message\":" + q(event.message())
                + ",\"evidence\":" + evidence(event.evidence()) + "}";
    }

    private String eventJson(SecurityEvent event) {
        return "{\"type\":" + q(event.getEventType().name())
                + ",\"source\":" + q(event.getSource())
                + ",\"outcome\":" + q(event.getOutcome())
                + ",\"level\":" + (event.getThreatLevel() == null ? "null" : q(event.getThreatLevel().name()))
                + ",\"message\":" + q(event.getMessage())
                + ",\"evidence\":" + evidence(event.getEvidence()) + "}";
    }

    private String evidence(Evidence evidence) {
        if (evidence == null) {
            return "null";
        }
        return "{\"failedAttempts\":" + evidence.getFailedAttemptCount()
                + ",\"windowMs\":" + evidence.getTimeWindowMs()
                + ",\"frequency\":" + evidence.getRequestFrequency()
                + ",\"thresholdExceeded\":" + evidence.isThresholdExceeded() + "}";
    }

    private String config() {
        return "{\"normalBandLimit\":" + detectionConfig.getNormalBandLimit()
                + ",\"suspiciousBandLimit\":" + detectionConfig.getSuspiciousBandLimit()
                + ",\"highBandLimit\":" + detectionConfig.getHighBandLimit()
                + ",\"timeWindowMs\":" + detectionConfig.getTimeWindowMs()
                + ",\"criticalStreakThreshold\":" + detectionConfig.getCriticalStreakThreshold()
                + ",\"maxFailurePoints\":" + detectionConfig.getMaxFailurePoints()
                + ",\"failureSaturationCount\":" + detectionConfig.getFailureSaturationCount()
                + ",\"maxFrequencyPoints\":" + detectionConfig.getMaxFrequencyPoints()
                + ",\"frequencySaturationRate\":" + detectionConfig.getFrequencySaturationRate()
                + ",\"maxDensityPoints\":" + detectionConfig.getMaxDensityPoints()
                + ",\"densitySaturationCount\":" + detectionConfig.getDensitySaturationCount()
                + ",\"maxPatternPoints\":" + detectionConfig.getMaxPatternPoints()
                + ",\"patternSaturationStreak\":" + detectionConfig.getPatternSaturationStreak()
                + ",\"decayIntervalMs\":" + detectionConfig.getDecayIntervalMs()
                + ",\"decayStepPerInterval\":" + detectionConfig.getDecayStepPerInterval()
                + ",\"highDelayMs\":" + protectionConfig.getHighDelayMs()
                + ",\"blockCooldownMs\":" + protectionConfig.getBlockCooldownMs()
                + ",\"reaperIntervalMs\":" + protectionConfig.getReaperIntervalMs() + "}";
    }

    private String applySetting(String key, String value) {
        if ("frequencySaturationRate".equals(key)) {
            detectionConfig.setFrequencySaturationRate(Double.parseDouble(value));
            return null;
        }
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException e) {
            return "Invalid value for " + key;
        }
        try {
            switch (key) {
                case "normalBandLimit" -> detectionConfig.setNormalBandLimit(Math.toIntExact(parsed));
                case "suspiciousBandLimit" -> detectionConfig.setSuspiciousBandLimit(Math.toIntExact(parsed));
                case "highBandLimit" -> detectionConfig.setHighBandLimit(Math.toIntExact(parsed));
                case "timeWindowMs" -> detectionConfig.setTimeWindowMs(parsed);
                case "criticalStreakThreshold" -> detectionConfig.setCriticalStreakThreshold(Math.toIntExact(parsed));
                case "maxFailurePoints" -> detectionConfig.setMaxFailurePoints(Math.toIntExact(parsed));
                case "failureSaturationCount" -> detectionConfig.setFailureSaturationCount(Math.toIntExact(parsed));
                case "maxFrequencyPoints" -> detectionConfig.setMaxFrequencyPoints(Math.toIntExact(parsed));
                case "maxDensityPoints" -> detectionConfig.setMaxDensityPoints(Math.toIntExact(parsed));
                case "densitySaturationCount" -> detectionConfig.setDensitySaturationCount(Math.toIntExact(parsed));
                case "maxPatternPoints" -> detectionConfig.setMaxPatternPoints(Math.toIntExact(parsed));
                case "patternSaturationStreak" -> detectionConfig.setPatternSaturationStreak(Math.toIntExact(parsed));
                case "decayIntervalMs" -> detectionConfig.setDecayIntervalMs(parsed);
                case "decayStepPerInterval" -> detectionConfig.setDecayStepPerInterval(Math.toIntExact(parsed));
                case "highDelayMs" -> protectionConfig.setHighDelayMs(parsed);
                case "blockCooldownMs" -> protectionConfig.setBlockCooldownMs(parsed);
                case "reaperIntervalMs" -> {
                    protectionConfig.setReaperIntervalMs(parsed);
                    protection.reschedule();
                }
                default -> {
                    return "Unknown setting: " + key;
                }
            }
        } catch (ArithmeticException e) {
            return "Invalid value for " + key;
        }
        return null;
    }

    private void refreshAssessments() {
        for (String id : tracker.sourceIds()) {
            detection.assess(id, Instant.now());
        }
    }

    private Map<String, String> queryParams(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    params.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
            }
        }
        return params;
    }

    private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String q(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static final class SseClient {
        private final OutputStream stream;
        private volatile boolean closed = false;

        SseClient(OutputStream stream) {
            this.stream = stream;
        }

        void write(String data) throws IOException {
            if (!closed) {
                stream.write(data.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }
        }

        boolean isClosed() {
            return closed;
        }

        void close() {
            closed = true;
            try {
                stream.close();
            } catch (IOException ignored) {
            }
        }
    }
}
