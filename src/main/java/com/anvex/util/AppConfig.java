package com.anvex.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static boolean initialized = false;

    public static final String SERVER_HOST = System.getProperty("anvex.host",
            System.getenv().getOrDefault("ANVEX_HOST", "127.0.0.1"));
    public static final int SERVER_PORT = 9090;
    public static final int API_PORT = Integer.parseInt(System.getProperty("anvex.apiPort",
            System.getenv().getOrDefault("ANVEX_API_PORT", "9091")));
    public static final int SERVER_HANDLER_POOL_SIZE = 24;
    public static final int ATTACKER_WORKER_POOL_SIZE = 8;
    public static final int ATTACK_SIZE = 2000;
    public static final int CORRECT_PASSWORD_INDEX = 1899;
    public static final int LOCKOUT_THRESHOLD = 5;
    public static final int LEGITIMATE_CLIENT_TIMEOUT_MS = 2000;

    private AppConfig() {}

    public static synchronized void initialize() {
        if (!initialized) {
            System.setProperty("java.util.logging.SimpleFormatter.format", 
                "%1$tH:%1$tM:%1$tS %4$s: %5$s%n");
            logger.info("ANVEX Configuration initialized");
            logger.info("Server: {}:{}", SERVER_HOST, SERVER_PORT);
            logger.info("Attack size: {}, Correct password index: {}", ATTACK_SIZE, CORRECT_PASSWORD_INDEX);
            logger.info("Lockout threshold: {}", LOCKOUT_THRESHOLD);
            initialized = true;
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
