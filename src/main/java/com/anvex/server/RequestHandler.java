package com.anvex.server;

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

public final class RequestHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);

    private final Socket clientSocket;
    private final UserRepository userRepository;
    private final EventBus eventBus;

    public RequestHandler(Socket clientSocket, UserRepository userRepository, EventBus eventBus) {
        this.clientSocket = clientSocket;
        this.userRepository = userRepository;
        this.eventBus = eventBus;
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String request = in.readLine();
            if (request == null || request.isEmpty()) {
                out.println("ERROR|Empty request");
                return;
            }

            logger.debug("Received request: {}", request);
            String response = processRequest(request);
            out.println(response);
            logger.debug("Sent response: {}", response);

        } catch (IOException e) {
            logger.error("Error handling request", e);
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                logger.debug("Error closing client socket", e);
            }
        }
    }

    private String processRequest(String request) {
        // Protocol: LOGIN|username|password|clientType
        String[] parts = request.split("\\|", 4);
        if (parts.length != 4 || !"LOGIN".equals(parts[0])) {
            return "ERROR|Invalid protocol";
        }

        String username = parts[1];
        String password = parts[2];
        String clientType = parts[3]; // ATTACKER or LEGITIMATE

        // Validate client type
        if (!"ATTACKER".equals(clientType) && !"LEGITIMATE".equals(clientType)) {
            return "ERROR|Invalid client type";
        }

        long runId = getCurrentRunId(); // Would be set by test/experiment

        boolean success = userRepository.verifyCredentials(username, password);
        
        SecurityEventType eventType;
        String outcome;
        
        if (success) {
            eventType = SecurityEventType.LOGIN_SUCCESS;
            outcome = "SUCCESS";
        } else {
            eventType = SecurityEventType.LOGIN_FAILURE;
            outcome = "FAILURE";
        }

        // Publish event
        SecurityEvent event = SecurityEvent.builder()
                .runId(runId)
                .eventType(eventType)
                .username(username)
                .clientType(clientType)
                .source("AuthenticationServer")
                .outcome(outcome)
                .message(String.format("Login %s for user %s", outcome.toLowerCase(), username))
                .build();
        eventBus.publish(event);

        return outcome;
    }

    private long getCurrentRunId() {
        // In real implementation, this would come from a thread-local or context
        // For now, return a default
        return 1;
    }
}