package com.anvex.server;

import com.anvex.defense.AuthenticationDecision;
import com.anvex.defense.DefenseEngine;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.UUID;

public final class RequestHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);
    private final Socket socket;
    private final UserRepository userRepository;
    private final EventBus eventBus;
    private final DefenseEngine defenseEngine;
    private final long runId;

    public RequestHandler(Socket socket, UserRepository userRepository, EventBus eventBus, long runId) {
        this(socket, userRepository, eventBus, new DefenseEngine(), runId);
    }

    public RequestHandler(Socket socket, UserRepository userRepository, EventBus eventBus,
                          DefenseEngine defenseEngine, long runId) {
        this.socket = socket;
        this.userRepository = userRepository;
        this.eventBus = eventBus;
        this.defenseEngine = defenseEngine;
        this.runId = runId;
    }

    @Override
    public void run() {
        String remoteAddress = socket.getInetAddress() == null
                ? "unknown" : socket.getInetAddress().getHostAddress();

        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            String request = in.readLine();
            if (request == null || request.isBlank()) {
                out.println("ERROR|EMPTY_REQUEST");
                return;
            }

            String[] parts = request.split("\\|", -1);
            if (parts.length != 4 || !"LOGIN".equals(parts[0])) {
                out.println("ERROR|INVALID_PROTOCOL");
                return;
            }

            String username = parts[1];
            String password = parts[2];
            String clientType = parts[3];
            if (username.isBlank() || password.isBlank() || clientType.isBlank()) {
                out.println("ERROR|INVALID_REQUEST");
                return;
            }

            AuthenticationDecision decision = defenseEngine.authenticate(
                    username, () -> userRepository.verifyCredentials(username, password));
            String observedClient = "LAB_REMOTE".equals(clientType)
                    ? "LAB_REMOTE@" + remoteAddress : clientType;

            if (decision == AuthenticationDecision.BLOCKED) {
                publishEvent(SecurityEventType.LOGIN_BLOCKED, username, observedClient,
                        remoteAddress, "BLOCKED", "Login blocked: account is locked");
                out.println("BLOCKED");
                return;
            }

            if (decision == AuthenticationDecision.SUCCESS) {
                publishEvent(SecurityEventType.LOGIN_SUCCESS, username, observedClient,
                        remoteAddress, "SUCCESS", "Login successful");
                out.println("SUCCESS");
                return;
            }

            publishEvent(SecurityEventType.LOGIN_FAILURE, username, observedClient,
                    remoteAddress, "FAILURE", "Invalid credentials");

            if (defenseEngine.isBlocked(username)) {
                publishEvent(SecurityEventType.ACCOUNT_LOCKED, username, observedClient,
                        remoteAddress, "LOCKED", "Account locked after repeated failed logins");
            }
            out.println("FAILURE");
        } catch (IOException e) {
            logger.debug("Client connection closed from {}: {}", remoteAddress, e.getMessage());
        }
    }

    private void publishEvent(SecurityEventType type, String username, String clientType,
                              String source, String outcome, String message) {
        eventBus.publish(SecurityEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .runId(runId)
                .eventType(type)
                .username(username)
                .clientType(clientType)
                .source(source)
                .outcome(outcome)
                .message(message)
                .build());
    }
}
