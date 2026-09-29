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
import com.anvex.protection.ProtectionAction;
import com.anvex.protection.ProtectionConfig;
import com.anvex.protection.ProtectionEngine;
import com.anvex.server.LoginProcessor;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    private final LoginProcessor loginProcessor;
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
        this(detection, tracker, protection, detectionConfig, protectionConfig, events, runs, metrics, bus,
                apiPort, tokenSupplier, null);
    }

    public DashboardApiServer(DetectionEngine detection, SourceActivityTracker tracker, ProtectionEngine protection,
                              DetectionConfig detectionConfig, ProtectionConfig protectionConfig,
                              EventRepository events, RunManager runs, MetricsCollector metrics, EventBus bus,
                              int apiPort, Supplier<String> tokenSupplier, LoginProcessor loginProcessor) {
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
        this.loginProcessor = loginProcessor;

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
        httpServer.createContext("/", this::handleStatic);
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
                case "POST" -> handlePost(exchange, route);
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

    private void handlePost(HttpExchange exchange, String route) throws IOException, SQLException, InterruptedException {
        if (route.equals("/runs")) {
            if (runs.getCurrentRunId() != 0 && !bus.awaitIdle(5000)) {
                sendJson(exchange, 503, "{\"error\":\"Previous run still processing\"}");
                return;
            }
            detection.reset();
            protection.reset();
            metrics.reset();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> parsed = JsonParser.parseFlat(body);
            String label = parsed.getOrDefault("label", "Lab Run");
            long runId = runs.startRun(label);
            sendJson(exchange, 200, "{\"runId\":" + runId + ",\"label\":" + q(label) + "}");
            return;
        }
        if (route.equals("/runs/end")) {
            long runId = runs.getCurrentRunId();
            if (runId == 0) {
                sendJson(exchange, 400, "{\"error\":\"No active run\"}");
                return;
            }
            runs.endRun();
            sendJson(exchange, 200, "{\"runId\":" + runId + ",\"status\":\"COMPLETED\"}");
            return;
        }
        if (route.equals("/simulate")) {
            if (loginProcessor == null) {
                sendJson(exchange, 503, "{\"error\":\"Simulator unavailable\"}");
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> parsed = JsonParser.parseFlat(body);
            String username = parsed.get("username");
            String password = parsed.get("password");
            String clientType = parsed.getOrDefault("clientType", "ATTACKER");
            String sourceId = parsed.get("sourceId");

            if (username == null || username.isEmpty() || username.length() > 100
                    || password == null || password.isEmpty() || password.length() > 512
                    || sourceId == null || sourceId.isEmpty() || sourceId.length() > 100 || sourceId.contains("|")
                    || (!"ATTACKER".equals(clientType) && !"LEGITIMATE".equals(clientType))) {
                sendJson(exchange, 400, "{\"error\":\"Invalid simulation request\"}");
                return;
            }

            ProtectionAction decision = protection.evaluateRequest(sourceId, Instant.now());
            boolean delayed = false;
            if (decision == ProtectionAction.BLOCK) {
                loginProcessor.publishBlockedEvent(username, clientType, sourceId);
                sendJson(exchange, 200, "{\"outcome\":\"BLOCKED\",\"protectionAction\":\"BLOCK\"}");
                return;
            }
            if (decision == ProtectionAction.DELAY) {
                try {
                    Thread.sleep(protection.getHighDelayMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                if (protection.evaluateRequest(sourceId, Instant.now()) == ProtectionAction.BLOCK) {
                    loginProcessor.publishBlockedEvent(username, clientType, sourceId);
                    sendJson(exchange, 200, "{\"outcome\":\"BLOCKED\",\"protectionAction\":\"BLOCK\"}");
                    return;
                }
                delayed = true;
            }

            String outcome = loginProcessor.attempt(username, password, clientType, sourceId, decision);
            if (delayed) {
                sendJson(exchange, 200, "{\"outcome\":\"" + outcome + "\",\"protectionAction\":\"DELAY\"}");
            } else {
                sendJson(exchange, 200, "{\"outcome\":\"" + outcome + "\"}");
            }
            return;
        }
        sendJson(exchange, 404, "{\"error\":\"Unknown endpoint\"}");
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            exchange.getResponseHeaders().set("Location", "/dashboard");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }

        Path staticDir = Paths.get(System.getProperty("anvex.staticDir",
                System.getenv().getOrDefault("ANVEX_STATIC_DIR", "frontend/dist")))
                .toAbsolutePath().normalize();
        String relative = path.equals("/lab") || path.equals("/dashboard")
                ? "index.html"
                : path.substring(1);
        Path target = staticDir.resolve(relative).normalize();

        if (!target.startsWith(staticDir) || !Files.isRegularFile(target)) {
            if (path.equals("/lab") || path.equals("/dashboard") || !relative.contains("/")) {
                sendJson(exchange, 404, "{\"error\":\"Frontend build missing. Run npm run build in frontend/\"}");
            } else {
                sendJson(exchange, 404, "{\"error\":\"Not found\"}");
            }
            return;
        }

        String contentType = contentTypeFor(target.getFileName().toString());
        byte[] bytes = Files.readAllBytes(target);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String contentTypeFor(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".json")) return "application/json";
        if (name.endsWith(".woff2")) return "font/woff2";
        if (name.endsWith(".map")) return "application/json";
        return "application/octet-stream";
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
        long eventCount;
        long alertCount;
        try {
            eventCount = events.countEvents();
            alertCount = events.countAlerts();
        } catch (SQLException e) {
            eventCount = -1;
            alertCount = -1;
        }
        return "{\"runId\":" + runs.getCurrentRunId()
                + ",\"trackedSources\":" + tracker.sourceIds().size()
                + ",\"eventCount\":" + eventCount
                + ",\"alertCount\":" + alertCount
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
