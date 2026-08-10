package com.anvex;

import com.anvex.client.LegitimateClientSimulator;
import com.anvex.event.EventBus;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.UserRepository;
import com.anvex.server.AuthenticationServer;
import com.anvex.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegitimateClientSimulatorTest {

    private static DatabaseManager dbManager;
    private static AuthenticationServer server;
    private static LegitimateClientSimulator client;

    @BeforeAll
    static void setUp() throws Exception {
        DatabaseManager.resetInstance();

        dbManager = DatabaseManager.getTestInstance();
        dbManager.initialize();

        UserRepository repository = new UserRepository(dbManager);

        repository.createUser(
                "legit_client",
                PasswordUtil.hashPassword("legit_password"),
                "LEGITIMATE"
        );

        server = new AuthenticationServer(
                dbManager,
                new EventBus()
        );

        server.start();

        Thread.sleep(300);

        client = new LegitimateClientSimulator();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop();
        }

        DatabaseManager.resetInstance();
    }

    @Test
    void legitimateClientCanAuthenticate() throws Exception {
        String response = client.authenticate(
                "legit_client",
                "legit_password"
        );

        assertEquals("SUCCESS", response);
    }

    @Test
    void legitimateClientWithWrongPasswordFails() throws Exception {
        String response = client.authenticate(
                "legit_client",
                "wrong_password"
        );

        assertEquals("FAILURE", response);
    }
}
