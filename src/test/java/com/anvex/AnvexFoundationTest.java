package com.anvex;

import com.anvex.util.AppConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnvexFoundationTest {

    @Test
    void appConfigInitializesCorrectly() {
        AppConfig.initialize();
        assertTrue(AppConfig.isInitialized());
    }

    @Test
    void coreParametersAreFrozen() {
        assertEquals("127.0.0.1", AppConfig.SERVER_HOST);
        assertEquals(9090, AppConfig.SERVER_PORT);
        assertEquals(24, AppConfig.SERVER_HANDLER_POOL_SIZE);
        assertEquals(8, AppConfig.ATTACKER_WORKER_POOL_SIZE);
        assertEquals(2000, AppConfig.ATTACK_SIZE);
        assertEquals(1899, AppConfig.CORRECT_PASSWORD_INDEX);
        assertEquals(5, AppConfig.LOCKOUT_THRESHOLD);
        assertEquals(2000, AppConfig.LEGITIMATE_CLIENT_TIMEOUT_MS);
    }

    @Test
    void lockoutThresholdIsMuchSmallerThanCorrectPasswordIndex() {
        assertTrue(AppConfig.LOCKOUT_THRESHOLD + AppConfig.ATTACKER_WORKER_POOL_SIZE < AppConfig.CORRECT_PASSWORD_INDEX);
    }
}