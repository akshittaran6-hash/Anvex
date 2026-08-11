package com.anvex.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class AppConfig {

    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static boolean initialized = false;

    /**
     * Defaults to loopback. For the live lab, set ANVEX_SERVER_HOST to the
     * server machine's private LAN IPv4 address. Public/wildcard binds are rejected.
     */
    public static final String SERVER_HOST = resolveServerHost();
    public static final int SERVER_PORT = 9090;
    public static final int SERVER_HANDLER_POOL_SIZE = 24;
    public static final int ATTACKER_WORKER_POOL_SIZE = 8;
    public static final int ATTACK_SIZE = 2000;
    public static final int CORRECT_PASSWORD_INDEX = 1899;
    public static final int LOCKOUT_THRESHOLD = 5;
    public static final int LEGITIMATE_CLIENT_TIMEOUT_MS = 2000;

    /** Live network mode is opt-in and remains limited to a private LAN address. */
    public static final boolean NETWORK_LAB_ENABLED =
            Boolean.parseBoolean(System.getenv().getOrDefault("ANVEX_NETWORK_LAB", "false"));
    public static final String NETWORK_LAB_TARGET_USERNAME =
            System.getenv().getOrDefault("ANVEX_LAB_TARGET_USERNAME", "lab_target");
    public static final String NETWORK_LAB_TARGET_PASSWORD =
            System.getenv().getOrDefault("ANVEX_LAB_TARGET_PASSWORD", "target123");
    public static final int NETWORK_LAB_MAX_ATTEMPTS = 20;
    public static final String NETWORK_LAB_CLIENT_TYPE = "LAB_REMOTE";

    private AppConfig() {}

    private static String resolveServerHost() {
        String configured = System.getenv().getOrDefault("ANVEX_SERVER_HOST", "127.0.0.1").trim();
        if (configured.isBlank() || "0.0.0.0".equals(configured) || "::".equals(configured)) {
            return "127.0.0.1";
        }

        try {
            InetAddress address = InetAddress.getByName(configured);
            if (!address.isLoopbackAddress() && !address.isSiteLocalAddress()) {
                logger.warn("Rejecting non-private ANVEX_SERVER_HOST={}; using 127.0.0.1", configured);
                return "127.0.0.1";
            }
            return configured;
        } catch (UnknownHostException e) {
            logger.warn("Unable to resolve ANVEX_SERVER_HOST={}; using 127.0.0.1", configured);
            return "127.0.0.1";
        }
    }

    public static synchronized void initialize() {
        if (!initialized) {
            System.setProperty("java.util.logging.SimpleFormatter.format",
                    "%1$tH:%1$tM:%1$tS %4$s: %5$s%n");
            logger.info("ANVEX Configuration initialized");
            logger.info("Server: {}:{}", SERVER_HOST, SERVER_PORT);
            logger.info("Attack size: {}, Correct password index: {}", ATTACK_SIZE, CORRECT_PASSWORD_INDEX);
            logger.info("Lockout threshold: {}", LOCKOUT_THRESHOLD);
            logger.info("Network lab: {}", NETWORK_LAB_ENABLED ? "ENABLED (private LAN only)" : "DISABLED");
            initialized = true;
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
