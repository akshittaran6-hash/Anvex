package com.anvex;

import com.anvex.attack.AttackClientSimulator;
import com.anvex.attack.AttackRunner;
import com.anvex.attack.LoginAttackScenario;
import com.anvex.event.CanonicalEventLog;
import com.anvex.event.EventBus;
import com.anvex.event.SecurityEventType;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.UserRepository;
import com.anvex.server.AuthenticationServer;
import com.anvex.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullAttackIntegrationTest {

    private static DatabaseManager dbManager;
    private static AuthenticationServer server;
    private static EventBus eventBus;
    private static CanonicalEventLog eventLog;

    @BeforeAll
    static void setUp() throws Exception {
        DatabaseManager.resetInstance();

        dbManager = DatabaseManager.getTestInstance();
        dbManager.initialize();

        UserRepository repository = new UserRepository(dbManager);

        repository.createUser(
                "lab_target",
                PasswordUtil.hashPassword("correct_password"),
                "TARGET"
        );

        eventBus = new EventBus();
        eventLog = new CanonicalEventLog();

        eventBus.subscribe(eventLog::record);

        server = new AuthenticationServer(
                dbManager,
                eventBus
        );

        server.start();

        Thread.sleep(300);
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop();
        }

        DatabaseManager.resetInstance();
    }

    @Test
    void fullAttackProducesCanonicalLoginEvents() throws Exception {
        eventLog.clearAll();
        eventBus.reset();

        LoginAttackScenario scenario =
                new LoginAttackScenario(
                        "lab_target",
                        "correct_password"
                );

        AttackClientSimulator client =
                new AttackClientSimulator();

        AttackRunner runner =
                new AttackRunner(
                        scenario,
                        client,
                        8
                );

        AttackRunner.Result result = runner.run();

        assertEquals(
                2000,
                result.getTotalAttempts()
        );

        assertEquals(
                2000,
                result.getCursorValue()
        );

        assertEquals(
                0,
                result.getErrors()
        );

        long loginEvents = eventLog.getAllEvents()
                .stream()
                .filter(event ->
                        event.getEventType() == SecurityEventType.LOGIN_FAILURE
                                || event.getEventType() == SecurityEventType.LOGIN_SUCCESS)
                .count();

        assertEquals(2000, loginEvents);

        long failures = eventLog.getAllEvents()
                .stream()
                .filter(event ->
                        event.getEventType()
                                == SecurityEventType.LOGIN_FAILURE)
                .count();

        long successes = eventLog.getAllEvents()
                .stream()
                .filter(event ->
                        event.getEventType()
                                == SecurityEventType.LOGIN_SUCCESS)
                .count();

        assertEquals(
                2000,
                failures + successes
        );

        assertTrue(failures > 0);
    }
}
