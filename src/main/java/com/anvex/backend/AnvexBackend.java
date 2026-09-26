package com.anvex.backend;

import com.anvex.detection.DetectionConfig;
import com.anvex.detection.DetectionEngine;
import com.anvex.detection.SourceActivityTracker;
import com.anvex.api.DashboardApiServer;
import com.anvex.event.EventBus;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.EventRepository;
import com.anvex.persistence.UserRepository;
import com.anvex.protection.ProtectionConfig;
import com.anvex.protection.ProtectionEngine;
import com.anvex.server.AuthenticationServer;
import com.anvex.server.LoginProcessor;
import com.anvex.server.RunManager;
import com.anvex.monitoring.MetricsCollector;
import com.anvex.util.AppConfig;
import com.anvex.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class AnvexBackend {

    private static final Logger logger = LoggerFactory.getLogger(AnvexBackend.class);

    private final EventBus eventBus;
    private final DetectionEngine detectionEngine;
    private final ProtectionEngine protectionEngine;
    private final EventRepository eventRepository;
    private final AuthenticationServer server;
    private final RunManager runManager;
    private final DashboardApiServer apiServer;

    private AnvexBackend(int port, int apiPort) throws Exception {
        java.nio.file.Files.createDirectories(java.nio.file.Path.of("data"));
        DatabaseManager dbManager = DatabaseManager.getInstance();
        dbManager.initialize();

        eventBus = new EventBus(dbManager.lastEventSequence());

        DetectionConfig detectionConfig = new DetectionConfig();
        ProtectionConfig protectionConfig = new ProtectionConfig();
        SourceActivityTracker tracker = new SourceActivityTracker();
        detectionEngine = new DetectionEngine(detectionConfig, tracker, eventBus);
        eventBus.subscribe(detectionEngine);

        runManager = new RunManager(dbManager, eventBus);
        protectionEngine = new ProtectionEngine(detectionEngine, protectionConfig, eventBus, runManager);
        eventBus.subscribe(protectionEngine);

        eventRepository = new EventRepository(dbManager);
        eventBus.subscribe(eventRepository);

        MetricsCollector metrics = new MetricsCollector();
        eventBus.subscribe(metrics::onEvent);

        LoginProcessor loginProcessor = new LoginProcessor(new UserRepository(dbManager), eventBus, runManager);

        apiServer = new DashboardApiServer(detectionEngine, tracker, protectionEngine,
                detectionConfig, protectionConfig, eventRepository, runManager, metrics, eventBus, apiPort,
                () -> System.getenv("ANVEX_API_TOKEN"), loginProcessor);

        seedDemoUsers(new UserRepository(dbManager));

        server = new AuthenticationServer(dbManager, eventBus, protectionEngine, port, runManager);
    }

    public void start() throws IOException {
        runManager.startRun("Backend session");
        server.start();
        protectionEngine.start();
        apiServer.start();
        logger.info("ANVEX backend is running. Press Ctrl+C to stop.");
    }

    public void stop() {
        apiServer.stop();
        server.stop();
        protectionEngine.stop();
        runManager.endRun();
        eventBus.shutdown();
        DatabaseManager.getInstance().shutdown();
        logger.info("ANVEX backend stopped");
    }

    private static void seedDemoUsers(UserRepository userRepository) {
        ensureUser(userRepository, "lab_target", "target123", "TARGET");
        ensureUser(userRepository, "legit_user_1", "legit1", "LEGITIMATE");
        ensureUser(userRepository, "legit_user_2", "legit2", "LEGITIMATE");
        ensureUser(userRepository, "legit_user_3", "legit3", "LEGITIMATE");
        ensureUser(userRepository, "legit_user_4", "legit4", "LEGITIMATE");
        ensureUser(userRepository, "legit_user_5", "legit5", "LEGITIMATE");
        logger.info("Demo users seeded");
    }

    private static void ensureUser(UserRepository userRepository, String username, String password, String role) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.createUser(username, PasswordUtil.hashPassword(password), role);
            logger.info("Created user: {} ({})", username, role);
        }
    }

    public static void main(String[] args) {
        AppConfig.initialize();
        if (!AppConfig.SERVER_HOST.equals("127.0.0.1") && !AppConfig.SERVER_HOST.equals("localhost")
                && (System.getenv("ANVEX_API_TOKEN") == null || System.getenv("ANVEX_API_TOKEN").isBlank())) {
            logger.error("Set ANVEX_API_TOKEN before binding the dashboard API to a network interface");
            System.exit(1);
        }
        int port = AppConfig.SERVER_PORT;
        int apiPort = AppConfig.API_PORT;
        try {
            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }
            if (args.length > 1) {
                apiPort = Integer.parseInt(args[1]);
            } else if (args.length > 0) {
                apiPort = port + 1;
            }
        } catch (NumberFormatException e) {
            logger.error("Invalid port argument");
            System.exit(1);
        }

        try {
            AnvexBackend backend = new AnvexBackend(port, apiPort);
            Runtime.getRuntime().addShutdownHook(new Thread(backend::stop, "AnvexBackend-Shutdown"));
            backend.start();
        } catch (Exception e) {
            logger.error("Failed to start ANVEX backend", e);
            System.exit(1);
        }
    }
}
