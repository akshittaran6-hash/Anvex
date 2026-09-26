package com.anvex.server;

import com.anvex.event.EventBus;
import com.anvex.persistence.UserRepository;
import com.anvex.protection.ProtectionAction;
import com.anvex.protection.ProtectionEngine;
import com.anvex.util.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

public final class RequestHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    private final Socket clientSocket;
    private final LoginProcessor loginProcessor;
    private final ProtectionEngine protectionEngine;
    private final RunManager runManager;
    private final ScheduledExecutorService delayPool;
    private final Semaphore delayedSlots;

    public RequestHandler(Socket clientSocket, UserRepository userRepository, EventBus eventBus,
                          ProtectionEngine protectionEngine) {
        this(clientSocket, new LoginProcessor(userRepository, eventBus, null), protectionEngine,
                null, null, new Semaphore(128));
    }

    public RequestHandler(Socket clientSocket, UserRepository userRepository, EventBus eventBus,
                          ProtectionEngine protectionEngine, RunManager runManager,
                          ScheduledExecutorService delayPool, Semaphore delayedSlots) {
        this(clientSocket, new LoginProcessor(userRepository, eventBus, runManager), protectionEngine,
                runManager, delayPool, delayedSlots);
    }

    private RequestHandler(Socket clientSocket, LoginProcessor loginProcessor, ProtectionEngine protectionEngine,
                           RunManager runManager, ScheduledExecutorService delayPool, Semaphore delayedSlots) {
        this.clientSocket = clientSocket;
        this.loginProcessor = loginProcessor;
        this.protectionEngine = protectionEngine;
        this.runManager = runManager;
        this.delayPool = delayPool;
        this.delayedSlots = delayedSlots;
    }

    @Override
    public void run() {
        Socket socket = this.clientSocket;
        try {
            socket.setSoTimeout(AppConfig.LEGITIMATE_CLIENT_TIMEOUT_MS);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);

            String request = readLimited(in, 2048);
            if (request == null || request.isEmpty()) {
                out.println("ERROR|Empty request");
                closeQuietly(socket);
                return;
            }

            String[] parts = request.split("\\|", 5);
            if (parts.length < 4 || !"LOGIN".equals(parts[0])) {
                out.println(handleNonLoginCommand(request));
                closeQuietly(socket);
                return;
            }

            String username = parts[1];
            String password = parts[2];
            String clientType = parts[3];
            String sourceId = (parts.length == 5 && !parts[4].isEmpty())
                    ? parts[4]
                    : resolveSourceAddress();

            if (username.length() > 100 || sourceId.length() > 100 || clientType.length() > 50
                    || password.length() > 512 || sourceId.contains("|")) {
                out.println("ERROR|Request field too long"); closeQuietly(socket); return;
            }

            if (!"ATTACKER".equals(clientType) && !"LEGITIMATE".equals(clientType)) {
                out.println("ERROR|Invalid client type");
                closeQuietly(socket);
                return;
            }

            ParsedRequest parsed = new ParsedRequest(username, password, clientType, sourceId);
            ProtectionAction decision = protectionEngine.evaluateRequest(sourceId, Instant.now());

            if (decision == ProtectionAction.BLOCK) {
                loginProcessor.publishBlockedEvent(parsed.username(), parsed.clientType(), parsed.sourceId());
                out.println("BLOCKED");
                closeQuietly(socket);
                return;
            }

            if (decision == ProtectionAction.DELAY && delayPool != null) {
                submitDelayed(socket, out, parsed);
                return;
            }

            if (decision == ProtectionAction.DELAY) {
                try { Thread.sleep(protectionEngine.getHighDelayMs()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); closeQuietly(socket); return; }
                if (protectionEngine.evaluateRequest(sourceId, Instant.now()) == ProtectionAction.BLOCK) {
                    loginProcessor.publishBlockedEvent(parsed.username(), parsed.clientType(), parsed.sourceId());
                    out.println("BLOCKED"); closeQuietly(socket); return;
                }
            }

            out.println(loginProcessor.attempt(parsed.username(), parsed.password(),
                    parsed.clientType(), parsed.sourceId(), decision));
            closeQuietly(socket);

        } catch (SocketTimeoutException e) {
            logger.debug("Idle client timed out: {}", socket.getRemoteSocketAddress());
            closeQuietly(socket);
        } catch (IOException e) {
            logger.error("Error handling request", e);
            closeQuietly(socket);
        }
    }

    private String handleNonLoginCommand(String request) {
        String token = System.getenv("ANVEX_API_TOKEN");
        if (token != null && !token.isBlank()) {
            String prefix = "AUTH|" + token + "|";
            if (!request.startsWith(prefix)) {
                return "ERROR|Unauthorized";
            }
            request = request.substring(prefix.length());
        }

        String[] parts = request.split("\\|", 2);
        String command = parts[0];
        switch (command) {
            case "START_RUN": {
                String label = parts.length > 1 ? parts[1] : null;
                if (runManager == null) {
                    return "ERROR|Run management unavailable";
                }
                long runId = runManager.startRun(label);
                return "RUN_STARTED|" + runId;
            }
            case "END_RUN": {
                if (runManager == null) {
                    return "ERROR|Run management unavailable";
                }
                long runId = runManager.getCurrentRunId();
                runManager.endRun();
                return "RUN_COMPLETED|" + runId;
            }
            default:
                return "ERROR|Invalid protocol";
        }
    }

    private void submitDelayed(Socket socket, PrintWriter out, ParsedRequest parsed) {
        if (!delayedSlots.tryAcquire()) { out.println("ERROR|Server busy"); closeQuietly(socket); return; }
        try { delayPool.schedule(() -> {
            try {
                if (protectionEngine.evaluateRequest(parsed.sourceId(), Instant.now()) == ProtectionAction.BLOCK) {
                    loginProcessor.publishBlockedEvent(parsed.username(), parsed.clientType(), parsed.sourceId());
                    out.println("BLOCKED");
                } else out.println(loginProcessor.attempt(parsed.username(), parsed.password(),
                        parsed.clientType(), parsed.sourceId(), ProtectionAction.DELAY));
            } catch (Exception e) {
                logger.error("Error processing delayed request", e);
                out.println("ERROR|Processing failed");
            } finally {
                delayedSlots.release();
                closeQuietly(socket);
            }
        }, protectionEngine.getHighDelayMs(), TimeUnit.MILLISECONDS); }
        catch (RejectedExecutionException e) { delayedSlots.release(); out.println("ERROR|Server busy"); closeQuietly(socket); }
    }

    private static String readLimited(BufferedReader in, int max) throws IOException {
        StringBuilder value = new StringBuilder(); int ch;
        while ((ch = in.read()) != -1 && ch != '\n') {
            if (value.length() >= max) throw new IOException("Request too long");
            if (ch != '\r') value.append((char) ch);
        }
        return ch == -1 && value.isEmpty() ? null : value.toString();
    }

    private String resolveSourceAddress() {
        java.net.InetAddress address = clientSocket.getInetAddress();
        return address != null ? address.getHostAddress() : "unknown";
    }

    private void closeQuietly(Socket socket) {
        try {
            if (!socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.debug("Error closing client socket", e);
        }
    }

    private record ParsedRequest(String username, String password, String clientType, String sourceId) {}
}
