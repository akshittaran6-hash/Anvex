package com.anvex.server;

import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.UserRepository;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class AuthenticationServer {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationServer.class);

    private final ServerSocket serverSocket;
    private final ExecutorService handlerPool;
    private final UserRepository userRepository;
    private final EventBus eventBus;
    private volatile boolean running = false;

    public AuthenticationServer(DatabaseManager dbManager, EventBus eventBus) throws IOException {
        this.eventBus = eventBus;
        this.userRepository = new UserRepository(dbManager);
        
        this.serverSocket = new ServerSocket(AppConfig.SERVER_PORT, 50, java.net.InetAddress.getByName(AppConfig.SERVER_HOST));
        
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "AuthServer-Handler-" + counter.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
        this.handlerPool = Executors.newFixedThreadPool(AppConfig.SERVER_HANDLER_POOL_SIZE, factory);
        
        logger.info("AuthenticationServer initialized on {}:{} with {} handler threads",
                AppConfig.SERVER_HOST, AppConfig.SERVER_PORT, AppConfig.SERVER_HANDLER_POOL_SIZE);
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
                handlerPool.submit(new RequestHandler(clientSocket, userRepository, eventBus));
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
        logger.info("AuthenticationServer stopped");
    }

    public boolean isRunning() {
        return running;
    }
}