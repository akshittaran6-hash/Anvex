package com.anvex;

import com.anvex.defense.DefenseEngine;
import com.anvex.defense.strategies.AccountLockoutDefense;
import com.anvex.detection.DetectionEngine;
import com.anvex.detection.rules.RepeatedFailedLoginRule;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.UserRepository;
import com.anvex.server.AuthenticationServer;
import com.anvex.util.AppConfig;
import com.anvex.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationDefenseIntegrationTest {

    private DatabaseManager db;
    private EventBus eventBus;
    private AuthenticationServer server;
    private List<SecurityEvent> events;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager.resetInstance();
        db = DatabaseManager.getTestInstance();
        db.initialize();

        UserRepository users = new UserRepository(db);
        users.createUser("integration_target", PasswordUtil.hashPassword("correct"), "TARGET");

        eventBus = new EventBus();
        events = new ArrayList<>();
        eventBus.subscribe(events::add);

        DetectionEngine detection = new DetectionEngine(List.of(new RepeatedFailedLoginRule()));
        server = new AuthenticationServer(db, eventBus, new DefenseEngine(), detection);
        server.enableDefense(new AccountLockoutDefense(5));
        server.start(1001);
        Thread.sleep(200);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        DatabaseManager.resetInstance();
    }

    @Test
    void repeatedFailuresTriggerDetectionAndLockout() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals("FAILURE", login("integration_target", "wrong"));
        }

        assertEquals("BLOCKED", login("integration_target", "correct"));
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == SecurityEventType.ALERT_TRIGGERED));
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == SecurityEventType.ACCOUNT_LOCKED));
        assertTrue(events.stream().anyMatch(e -> e.getEventType() == SecurityEventType.LOGIN_BLOCKED));
    }

    @Test
    void correctCredentialsStillWorkBeforeLockout() throws Exception {
        assertEquals("SUCCESS", login("integration_target", "correct"));
        assertFalse(events.stream().anyMatch(e -> e.getEventType() == SecurityEventType.LOGIN_BLOCKED));
    }

    private String login(String username, String password) throws Exception {
        try (Socket socket = new Socket(AppConfig.SERVER_HOST, AppConfig.SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out.println("LOGIN|" + username + "|" + password + "|ATTACKER");
            return in.readLine();
        }
    }
}
