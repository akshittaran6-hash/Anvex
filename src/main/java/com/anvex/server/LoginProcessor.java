package com.anvex.server;

import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.persistence.UserRepository;
import com.anvex.protection.ProtectionAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public final class LoginProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LoginProcessor.class);

    private final UserRepository userRepository;
    private final EventBus eventBus;
    private final RunManager runManager;

    public LoginProcessor(UserRepository userRepository, EventBus eventBus, RunManager runManager) {
        this.userRepository = userRepository;
        this.eventBus = eventBus;
        this.runManager = runManager;
    }

    public String attempt(String username, String password, String clientType, String sourceId,
                          ProtectionAction decision) {
        long runId = currentRunId();

        boolean success;
        try {
            success = userRepository.verifyCredentials(username, password);
        } catch (IllegalStateException e) {
            logger.error("Authentication database unavailable", e);
            return "ERROR|Database unavailable";
        }

        SecurityEventType eventType = success ? SecurityEventType.LOGIN_SUCCESS : SecurityEventType.LOGIN_FAILURE;
        String outcome = success ? "SUCCESS" : "FAILURE";

        SecurityEvent.Builder eventBuilder = SecurityEvent.builder()
                .runId(runId)
                .eventType(eventType)
                .username(username)
                .clientType(clientType)
                .source(sourceId)
                .outcome(outcome)
                .message(String.format("Login %s for user %s from %s",
                        outcome.toLowerCase(), username, sourceId));

        if (decision == ProtectionAction.DELAY) {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("protectionAction", "DELAY");
            eventBuilder.metadata(metadata);
        }

        eventBus.publish(eventBuilder.build());
        logger.debug("Login outcome: {}", outcome);
        return outcome;
    }

    public void publishBlockedEvent(String username, String clientType, String sourceId) {
        eventBus.publish(SecurityEvent.builder()
                .runId(currentRunId())
                .eventType(SecurityEventType.LOGIN_BLOCKED)
                .username(username)
                .clientType(clientType)
                .source(sourceId)
                .outcome("BLOCKED")
                .message(String.format("Login refused: source %s is under active protection", sourceId))
                .build());
    }

    private long currentRunId() {
        return runManager != null ? runManager.ensureActiveRun() : 1;
    }
}
