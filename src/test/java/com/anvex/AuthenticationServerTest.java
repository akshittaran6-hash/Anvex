package com.anvex;

import com.anvex.detection.DetectionConfig;
import com.anvex.detection.DetectionEngine;
import com.anvex.detection.SourceActivityTracker;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEvent;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.UserRepository;
import com.anvex.protection.ProtectionConfig;
import com.anvex.protection.ProtectionEngine;
import com.anvex.server.AuthenticationServer;
import com.anvex.util.AppConfig;
import com.anvex.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationServerTest {

    private static DatabaseManager dbManager;
    private static EventBus eventBus;
    private static AuthenticationServer server;
    private static UserRepository userRepository;
    private static ExecutorService clientPool;

    @BeforeAll
    static void setUp() throws IOException, InterruptedException, SQLException {
        DatabaseManager.resetInstance();
        dbManager = DatabaseManager.getTestInstance();
        dbManager.initialize();
        
        eventBus = new EventBus();
        userRepository = new UserRepository(dbManager);
        
        // Insert test users
        insertTestUsers();
        
        // Start server with an unsubscribed protection engine (always ALLOW)
        DetectionEngine detectionEngine = new DetectionEngine(
                new DetectionConfig(), new SourceActivityTracker(), eventBus);
        server = new AuthenticationServer(dbManager, eventBus,
                new ProtectionEngine(detectionEngine, new ProtectionConfig(), eventBus));
        server.start();
        
        // Wait for server to be ready
        Thread.sleep(500);
        
        clientPool = Executors.newFixedThreadPool(10);
    }

    @AfterAll
    static void tearDown() {
        if (server != null) server.stop();
        if (clientPool != null) clientPool.shutdown();
        DatabaseManager.resetInstance();
    }

    private static void insertTestUsers() {
        userRepository.createUser("test_user", PasswordUtil.hashPassword("password123"), "LEGITIMATE");
        userRepository.createUser("target_user", PasswordUtil.hashPassword("targetpass"), "TARGET");
        userRepository.createUser("attacker_target", PasswordUtil.hashPassword("wrongpass"), "TARGET");
    }

    @Test
    void correctCredentialsReturnSuccess() throws IOException, InterruptedException {
        String response = sendLoginRequest("test_user", "password123", "LEGITIMATE");
        assertEquals("SUCCESS", response);
    }

    @Test
    void incorrectPasswordReturnsFailure() throws IOException, InterruptedException {
        String response = sendLoginRequest("test_user", "wrongpass", "LEGITIMATE");
        assertEquals("FAILURE", response);
    }

    @Test
    void nonExistentUserReturnsFailure() throws IOException, InterruptedException {
        String response = sendLoginRequest("nonexistent", "password", "LEGITIMATE");
        assertEquals("FAILURE", response);
    }

    @Test
    void attackerClientTypeWorks() throws IOException, InterruptedException {
        String response = sendLoginRequest("target_user", "targetpass", "ATTACKER");
        assertEquals("SUCCESS", response);
    }

    @Test
    void concurrentRequestsHandled() throws InterruptedException {
        int numClients = 20;
        CountDownLatch latch = new CountDownLatch(numClients);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < numClients; i++) {
            clientPool.submit(() -> {
                try {
                    String response = sendLoginRequest("test_user", "password123", "LEGITIMATE");
                    if ("SUCCESS".equals(response)) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (IOException e) {
                    failureCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All requests should complete");
        assertEquals(numClients, successCount.get());
        assertEquals(0, failureCount.get());
    }

    @Test
    void invalidProtocolReturnsError() throws IOException, InterruptedException {
        String response = sendRawRequest("INVALID|test|pass|LEGITIMATE");
        assertTrue(response.startsWith("ERROR"));
    }

    @Test
    void emptyRequestReturnsError() throws IOException, InterruptedException {
        String response = sendRawRequest("");
        assertTrue(response.startsWith("ERROR"));
    }

    @Test
    void sourceIdFromProtocolUsedInEvents() throws Exception {
        userRepository.createUser("source_test_a", PasswordUtil.hashPassword("pass123"), "LEGITIMATE");
        List<SecurityEvent> captured = new CopyOnWriteArrayList<>();
        eventBus.subscribe(captured::add);

        String response = sendLoginRequest("source_test_a", "pass123", "LEGITIMATE", "192.168.1.50");
        assertEquals("SUCCESS", response);
        assertTrue(eventBus.awaitIdle(2000), "Event should be processed");

        SecurityEvent event = captured.stream()
                .filter(e -> "source_test_a".equals(e.getUsername()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No event captured for source_test_a"));
        assertEquals("192.168.1.50", event.getSource());
    }

    @Test
    void missingSourceIdFallsBackToSocketAddress() throws Exception {
        userRepository.createUser("source_test_b", PasswordUtil.hashPassword("pass456"), "LEGITIMATE");
        List<SecurityEvent> captured = new CopyOnWriteArrayList<>();
        eventBus.subscribe(captured::add);

        String response = sendLoginRequest("source_test_b", "pass456", "LEGITIMATE");
        assertEquals("SUCCESS", response);
        assertTrue(eventBus.awaitIdle(2000), "Event should be processed");

        SecurityEvent event = captured.stream()
                .filter(e -> "source_test_b".equals(e.getUsername()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No event captured for source_test_b"));
        assertEquals(AppConfig.SERVER_HOST, event.getSource());
    }

    private String sendLoginRequest(String username, String password, String clientType)
            throws IOException, InterruptedException {
        return sendRawRequest("LOGIN|" + username + "|" + password + "|" + clientType);
    }

    private String sendLoginRequest(String username, String password, String clientType, String sourceId)
            throws IOException, InterruptedException {
        return sendRawRequest("LOGIN|" + username + "|" + password + "|" + clientType + "|" + sourceId);
    }

    private String sendRawRequest(String request) throws IOException, InterruptedException {
        try (Socket socket = new Socket(AppConfig.SERVER_HOST, AppConfig.SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(request);
            return in.readLine();
        }
    }
}