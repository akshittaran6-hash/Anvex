package com.anvex.server;

import com.anvex.defense.DefenseEngine;
import com.anvex.defense.DefenseStrategy;
import com.anvex.detection.DetectionEngine;
import com.anvex.detection.rules.RepeatedFailedLoginRule;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.UserRepository;
import com.anvex.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class AuthenticationServer {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationServer.class);

    private final DatabaseManager dbManager;
    private final EventBus eventBus;
    private final UserRepository userRepository;
    private final ExecutorService handlerPool;
    private final DefenseEngine defenseEngine;
    private final DetectionEngine detectionEngine;

    private final AtomicLong currentRunId = new AtomicLong(0);

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public AuthenticationServer(DatabaseManager dbManager, EventBus eventBus) {
        this(dbManager, eventBus, new DefenseEngine(), createDefaultDetectionEngine());
    }

    public AuthenticationServer(DatabaseManager dbManager, EventBus eventBus,
                                 DefenseEngine defenseEngine) {
        this(dbManager, eventBus, defenseEngine, createDefaultDetectionEngine());
    }

    public AuthenticationServer(DatabaseManager dbManager, EventBus eventBus,
                                 DefenseEngine defenseEngine,
                                 DetectionEngine detectionEngine) {
        if (dbManager == null) {
            throw new IllegalArgumentException("dbManager cannot be null");
        }
        if (eventBus == null) {
            throw new IllegalArgumentException("eventBus cannot be null");
        }
        if (defenseEngine == null) {
            throw new IllegalArgumentException("defenseEngine cannot be null");
        }
        if (detectionEngine == null) {
            throw new IllegalArgumentException("detectionEngine cannot be null");
        }

        this.dbManager = dbManager;
        this.eventBus = eventBus;
        this.userRepository = new UserRepository(dbManager);
        this.defenseEngine = defenseEngine;
        this.detectionEngine = detectionEngine;
        this.handlerPool = Executors.newFixedThreadPool(AppConfig.SERVER_HANDLER_POOL_SIZE);

        // Detection observes the same canonical event stream as persistence and other
        // subscribers. Alerts are fed back into that stream as ALERT_TRIGGERED events.
        eventBus.subscribe(detectionEngine);
        detectionEngine.setAlertListener(alert -> eventBus.publish(DetectionEngine.toAlertEvent(alert)));
    }

    private static DetectionEngine createDefaultDetectionEngine() {
        DetectionEngine engine = new DetectionEngine();
        engine.addRule(new RepeatedFailedLoginRule());
        return engine;
    }

    public synchronized void start() throws IOException {
        start(0);
    }

    public synchronized void start(long runId) throws IOException {
        if (running) {
            return;
        }

        currentRunId.set(runId);

        serverSocket = new ServerSocket(
                AppConfig.SERVER_PORT,
                50,
                java.net.InetAddress.getByName(AppConfig.SERVER_HOST));

        running = true;

        publishEvent(
                SecurityEventType.SERVER_STARTED,
                null,
                null,
                "SERVER",
                "STARTED",
                "Authentication server started");

        acceptThread = new Thread(this::acceptLoop, "anvex-auth-server");
        acceptThread.setDaemon(true);
        acceptThread.start();

        logger.info("Authentication server started on {}:{}", AppConfig.SERVER_HOST, AppConfig.SERVER_PORT);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                handlerPool.submit(new RequestHandler(
                        socket,
                        userRepository,
                        eventBus,
                        defenseEngine,
                        currentRunId.get()));
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting client connection", e);
                }
            }
        }
    }

    /** Enables exactly one active defense strategy, replacing any previous strategies. */
    public synchronized void enableDefense(DefenseStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("strategy cannot be null");
        }
        defenseEngine.clearStrategies();
        defenseEngine.addStrategy(strategy);
        publishEvent(
                SecurityEventType.DEFENSE_ENABLED,
                null,
                "SYSTEM",
                strategy.getName(),
                "ENABLED",
                "Defense enabled: " + strategy.getName());
    }

    /** Disables all active authentication defenses without deleting historical data. */
    public synchronized void disableDefense() {
        defenseEngine.clearStrategies();
        publishEvent(
                SecurityEventType.DEFENSE_DISABLED,
                null,
                "SYSTEM",
                "DEFENSE_ENGINE",
                "DISABLED",
                "All authentication defenses disabled");
    }

    /** Clears live defense state, including account failure counters and locks. */
    public synchronized void resetDefense() {
        defenseEngine.reset();
    }

    /** Clears in-memory detection state while retaining historical events. */
    public synchronized void resetDetection() {
        detectionEngine.reset();
    }

    public boolean isDefenseEnabled() {
        return !defenseEngine.isEmpty();
    }

    public DefenseEngine getDefenseEngine() {
        return defenseEngine;
    }

    public DetectionEngine getDetectionEngine() {
        return detectionEngine;
    }

    private void publishEvent(
            SecurityEventType type,
            String username,
            String clientType,
            String source,
            String outcome,
            String message) {

        SecurityEvent event = SecurityEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .runId(currentRunId.get())
                .eventType(type)
                .username(username)
                .clientType(clientType)
                .source(source)
                .outcome(outcome)
                .message(message)
                .build();

        eventBus.publish(event);
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            logger.warn("Error closing server socket", e);
        }

        if (acceptThread != null) {
            acceptThread.interrupt();
        }

        handlerPool.shutdown();

        try {
            if (!handlerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                handlerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            handlerPool.shutdownNow();
        }

        publishEvent(
                SecurityEventType.SERVER_STOPPED,
                null,
                null,
                "SERVER",
                "STOPPED",
                "Authentication server stopped");

        logger.info("Authentication server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public long getCurrentRunId() {
        return currentRunId.get();
    }
}
