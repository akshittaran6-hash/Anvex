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
import java.util.UUID;

public final class RequestHandler implements Runnable {

    private static final Logger logger =
            LoggerFactory.getLogger(RequestHandler.class);

    private final Socket socket;
    private final UserRepository userRepository;
    private final EventBus eventBus;
private final long runId;

    public RequestHandler(
            Socket socket,
            UserRepository userRepository,
            EventBus eventBus, long runId) {
        this.socket = socket;
        this.userRepository = userRepository;
        this.eventBus = eventBus;
        this.runId = runId;
    }

    @Override
    public void run() {
        try (socket;
             BufferedReader in = new BufferedReader(
                     new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(
                     socket.getOutputStream(), true)) {

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

            if (username.isBlank()
                    || password.isBlank()
                    || clientType.isBlank()) {
                out.println("ERROR|INVALID_REQUEST");
                return;
            }

            boolean valid =
                    userRepository.verifyCredentials(username, password);

            if (valid) {
                publishEvent(
                        SecurityEventType.LOGIN_SUCCESS,
                        username,
                        clientType,
                        "AUTHENTICATION_SERVER",
                        "SUCCESS",
                        "Login successful"
                );

                out.println("SUCCESS");
            } else {
                publishEvent(
                        SecurityEventType.LOGIN_FAILURE,
                        username,
                        clientType,
                        "AUTHENTICATION_SERVER",
                        "FAILURE",
                        "Invalid credentials"
                );

                out.println("FAILURE");
            }

        } catch (IOException e) {
            logger.debug("Client connection closed: {}", e.getMessage());
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
                .runId(runId)
                .eventType(type)
                .username(username)
                .clientType(clientType)
                .source(source)
                .outcome(outcome)
                .message(message)
                .build();

        eventBus.publish(event);
    }
}
