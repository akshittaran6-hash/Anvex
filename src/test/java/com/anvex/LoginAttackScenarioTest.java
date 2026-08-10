package com.anvex;

import com.anvex.attack.LoginAttackScenario;
import com.anvex.util.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginAttackScenarioTest {

    @Test
    void scenarioHasConfiguredAttackSize() {
        LoginAttackScenario scenario =
                new LoginAttackScenario("lab_target", "correct_password");

        assertEquals(
                AppConfig.ATTACK_SIZE,
                scenario.getAttemptCount()
        );
    }

    @Test
    void correctPasswordAppearsAtConfiguredIndex() {
        LoginAttackScenario scenario =
                new LoginAttackScenario("lab_target", "correct_password");

        assertEquals(
                "correct_password",
                scenario.getPassword(AppConfig.CORRECT_PASSWORD_INDEX)
        );
    }

    @Test
    void otherAttemptsAreIncorrect() {
        LoginAttackScenario scenario =
                new LoginAttackScenario("lab_target", "correct_password");

        assertNotEquals(
                "correct_password",
                scenario.getPassword(0)
        );

        assertNotEquals(
                "correct_password",
                scenario.getPassword(
                        AppConfig.CORRECT_PASSWORD_INDEX - 1
                )
        );
    }

    @Test
    void targetUsernameIsPreserved() {
        LoginAttackScenario scenario =
                new LoginAttackScenario("lab_target", "correct_password");

        assertEquals(
                "lab_target",
                scenario.getTargetUsername()
        );
    }
}
