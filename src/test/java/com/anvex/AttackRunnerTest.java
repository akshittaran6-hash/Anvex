package com.anvex;

import com.anvex.attack.AttackClientSimulator;
import com.anvex.attack.AttackRunner;
import com.anvex.attack.LoginAttackScenario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttackRunnerTest {

    @Test
    void scenarioIsConfiguredForFullAttack() {
        LoginAttackScenario scenario =
                new LoginAttackScenario(
                        "lab_target",
                        "correct_password"
                );

        assertEquals(2000, scenario.getAttemptCount());
    }

    @Test
    void resultStoresExecutionStatistics() {
        AttackRunner.Result result =
                new AttackRunner.Result(
                        2000,
                        1,
                        1999,
                        0,
                        2000
                );

        assertEquals(2000, result.getTotalAttempts());
        assertEquals(1, result.getSuccessfulAttempts());
        assertEquals(1999, result.getFailedAttempts());
        assertEquals(0, result.getErrors());
        assertEquals(2000, result.getCursorValue());
    }

    @Test
    void runnerRejectsInvalidWorkerCount() {
        LoginAttackScenario scenario =
                new LoginAttackScenario(
                        "lab_target",
                        "correct_password"
                );

        AttackClientSimulator client =
                new AttackClientSimulator();

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new AttackRunner(scenario, client, 0)
        );
    }
}
