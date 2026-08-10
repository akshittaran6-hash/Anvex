package com.anvex;

import com.anvex.attack.AttackClientSimulator;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.UserRepository;
import com.anvex.server.AuthenticationServer;
import com.anvex.util.AppConfig;
import com.anvex.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttackClientSimulatorTest {

    private static DatabaseManager dbManager;
    private static AuthenticationServer server;
    private static AttackClientSimulator attacker;

    @BeforeAll
    static void setUp() throws Exception {
        DatabaseManager.resetInstance();
        dbManager = DatabaseManager.getTestInstance();
        dbManager.initialize();

        UserRepository repository = new UserRepository(dbManager);

        repository.createUser(
                "attack_target",
                PasswordUtil.hashPassword("correct_password"),
                "TARGET"
        );

        server = new AuthenticationServer(
                dbManager,
                new com.anvex.event.EventBus()
        );

        server.start();

        Thread.sleep(300);

        attacker = new AttackClientSimulator();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop();
        }

        DatabaseManager.resetInstance();
    }

    @Test
    void attackerCanSendRealSocketRequest() throws Exception {
        String response = attacker.attempt(
                "attack_target",
                "wrong_password"
        );

        assertEquals("FAILURE", response);
    }

    @Test
    void attackerCanReachCorrectPassword() throws Exception {
        String response = attacker.attempt(
                "attack_target",
                "correct_password"
        );

        assertEquals("SUCCESS", response);
    }
}
