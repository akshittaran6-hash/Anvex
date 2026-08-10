package com.anvex;

import com.anvex.attack.BeforeAfterExperiment;
import com.anvex.attack.LoginAttackScenario;
import com.anvex.event.CanonicalEventLog;
import com.anvex.event.EventBus;
import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.UserRepository;
import com.anvex.monitoring.ComparisonResult;
import com.anvex.defense.strategies.AccountLockoutDefense;
import com.anvex.server.AuthenticationServer;
import com.anvex.util.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BeforeAfterExperimentTest {

    private DatabaseManager db;
    private EventBus eventBus;
    private CanonicalEventLog eventLog;
    private AuthenticationServer server;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseManager.resetInstance();

        db = DatabaseManager.getTestInstance();
        db.initialize();

        UserRepository users = new UserRepository(db);

        users.createUser(
                "lab_target",
                PasswordUtil.hashPassword("correct_password"),
                "TARGET"
        );

        users.createUser(
                "legit_user_1",
                PasswordUtil.hashPassword("legit_password"),
                "LEGITIMATE"
        );

        eventBus = new EventBus();
        eventLog = new CanonicalEventLog();
        eventBus.subscribe(eventLog::record);

        server = new AuthenticationServer(db, eventBus);
        server.start();
        Thread.sleep(250);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        DatabaseManager.resetInstance();
    }

    @Test
    void beforeAfterExperimentProducesCompromisedThenProtectedRuns() throws Exception {
        BeforeAfterExperiment experiment =
                new BeforeAfterExperiment(
                        db,
                        server,
                        eventBus,
                        eventLog,
                        8
                );

        ComparisonResult result = experiment.run(
                new LoginAttackScenario(
                        "lab_target",
                        "correct_password"
                ),
                new AccountLockoutDefense()
        );

        assertTrue(result.beforeCompromised());
        assertTrue(result.afterProtected());

        assertEquals(2000, result.before().totalAttackerAttempts());
        assertEquals(2000, result.after().totalAttackerAttempts());

        assertEquals(1, result.before().attackerSuccesses());
        assertEquals(0, result.after().attackerSuccesses());

        assertTrue(result.before().alertCount() > 0);
        assertTrue(result.after().alertCount() > 0);

        assertTrue(result.after().attackerBlocked() > 0);
    }
}
