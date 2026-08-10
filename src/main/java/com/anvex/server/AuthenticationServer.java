package com.anvex.server;

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

    private static final Logger logger =
            LoggerFactory.getLogger(AuthenticationServer.class);

    private final DatabaseManager dbManager;
    private final EventBus eventBus;
    private final UserRepository userRepository;
    private final ExecutorService handlerPool;

    private final AtomicLong currentRunId =
            new AtomicLong(0);

    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread acceptThread;

    public AuthenticationServer(
            DatabaseManager dbManager,
            EventBus eventBus) {

        this.dbManager = dbManager;
        this.eventBus = eventBus;
        this.userRepository = new UserRepository(dbManager);

        this.handlerPool = Executors.newFixedThreadPool(
                AppConfig.SERVER_HANDLER_POOL_SIZE);
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
                java.net.InetAddress.getByName(
                        AppConfig.SERVER_HOST));

        running = true;

        publishEvent(
                SecurityEventType.SERVER_STARTED,
                null,
                null,
                "SERVER",
                "STARTED",
                "Authentication server started"
        );

        acceptThread = new Thread(
                this::acceptLoop,
                "anvex-auth-server");

        acceptThread.setDaemon(true);
        acceptThread.start();

        logger.info(
                "Authentication server started on {}:{}",
                AppConfig.SERVER_HOST,
                AppConfig.SERVER_PORT);
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();

                handlerPool.submit(
                        new RequestHandler(
                                socket,
                                userRepository,
                                eventBus,
                                currentRunId.get()));

            } catch (IOException e) {
                if (running) {
                    logger.error(
                            "Error accepting client connection",
                            e);
                }
            }
        }
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
            if (!handlerPool.awaitTermination(
                    2,
                    TimeUnit.SECONDS)) {

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
                "Authentication server stopped"
        );

        logger.info("Authentication server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public long getCurrentRunId() {
        return currentRunId.get();
    }
}
