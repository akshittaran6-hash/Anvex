package com.anvex.server;

import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.UserRepository;
import com.anvex.protection.ProtectionEngine;
import com.anvex.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class AuthenticationServer {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationServer.class);

    private final ServerSocket serverSocket;
    private final ExecutorService handlerPool;
    private final ScheduledExecutorService delayPool;
    private final Semaphore delayedSlots = new Semaphore(128);
    private final RunManager runManager;
    private final UserRepository userRepository;
    private final EventBus eventBus;
    private final ProtectionEngine protectionEngine;
    private final int port;
    private volatile boolean running = false;

    public AuthenticationServer(DatabaseManager dbManager, EventBus eventBus, ProtectionEngine protectionEngine) throws IOException {
        this(dbManager, eventBus, protectionEngine, AppConfig.SERVER_PORT);
    }

    public AuthenticationServer(DatabaseManager dbManager, EventBus eventBus,
                                ProtectionEngine protectionEngine, int port) throws IOException {
        this(dbManager, eventBus, protectionEngine, port, null);
    }

    public AuthenticationServer(DatabaseManager dbManager, EventBus eventBus,
                                ProtectionEngine protectionEngine, int port, RunManager runManager) throws IOException {
        this.eventBus = eventBus;
        this.protectionEngine = protectionEngine;
        this.port = port;
        this.runManager = runManager;
        this.userRepository = new UserRepository(dbManager);
        
        this.serverSocket = new ServerSocket(port, 50, java.net.InetAddress.getByName(AppConfig.SERVER_HOST));
        
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "AuthServer-Handler-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        this.handlerPool = new ThreadPoolExecutor(AppConfig.SERVER_HANDLER_POOL_SIZE,
                AppConfig.SERVER_HANDLER_POOL_SIZE, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(96), factory, new ThreadPoolExecutor.AbortPolicy());
        this.delayPool = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "AuthServer-Delay"); t.setDaemon(true); return t;
        });
        
        logger.info("AuthenticationServer initialized on {}:{} with {} handler threads",
                AppConfig.SERVER_HOST, port, AppConfig.SERVER_HANDLER_POOL_SIZE);
    }

    public void start() {
        if (running) return;
        running = true;
        
        new Thread(this::acceptLoop, "AuthServer-Acceptor").start();
        logger.info("AuthenticationServer started");
    }

    private void acceptLoop() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                try {
                    handlerPool.submit(new RequestHandler(clientSocket, userRepository, eventBus,
                        protectionEngine, runManager, delayPool, delayedSlots));
                } catch (RejectedExecutionException e) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                if (running) {
                    logger.error("Error accepting connection", e);
                }
            }
        }
    }

    public void stop() {
        running = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            logger.error("Error closing server socket", e);
        }
        handlerPool.shutdown();
        delayPool.shutdown();
        try {
            if (!handlerPool.awaitTermination(5, TimeUnit.SECONDS)) handlerPool.shutdownNow();
            if (!delayPool.awaitTermination(5, TimeUnit.SECONDS)) delayPool.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); handlerPool.shutdownNow(); delayPool.shutdownNow();
        }
        logger.info("AuthenticationServer stopped");
    }

    public boolean isRunning() {
        return running;
    }

    public int getLocalPort() { return serverSocket.getLocalPort(); }
}
