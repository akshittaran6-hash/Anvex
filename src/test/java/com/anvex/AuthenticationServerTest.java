package com.anvex;

import com.anvex.event.EventBus;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.UserRepository;
import com.anvex.server.AuthenticationServer;
import com.anvex.util.AppConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
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
    static void setUp() throws IOException, InterruptedException {
        DatabaseManager.resetInstance();
        dbManager = DatabaseManager.getTestInstance();
        dbManager.initialize();
        
        eventBus = new EventBus();
        userRepository = new UserRepository(dbManager);
        
        // Insert test users
        insertTestUsers();
        
        // Start server
        server = new AuthenticationServer(dbManager, eventBus);
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
        userRepository.createUser("test_user", "password123", "LEGITIMATE");
        userRepository.createUser("target_user", "targetpass", "TARGET");
        userRepository.createUser("attacker_target", "wrongpass", "TARGET");
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

    private String sendLoginRequest(String username, String password, String clientType) 
            throws IOException, InterruptedException {
        return sendRawRequest("LOGIN|" + username + "|" + password + "|" + clientType);
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