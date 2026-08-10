package com.anvex;

import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.User;
import com.anvex.persistence.UserRepository;
import com.anvex.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseUsersTest {

    private static DatabaseManager dbManager;
    private static UserRepository userRepository;

    @BeforeAll
    static void setUp() throws SQLException {
        dbManager = DatabaseManager.getInstance();
        dbManager.initialize();
        userRepository = new UserRepository(dbManager);
    }

    @AfterAll
    static void tearDown() {
        if (dbManager != null) {
            dbManager.shutdown();
        }
    }

    @Test
    void databaseInitializesWithDemoUsers() {
        List<User> users = userRepository.findAll();
        assertEquals(6, users.size(), "Should have 6 demo users (1 target + 5 legitimate)");
    }

    @Test
    void targetAccountExists() {
        Optional<User> target = userRepository.findByUsername("lab_target");
        assertTrue(target.isPresent());
        assertTrue(target.get().isTarget());
        assertEquals("TARGET", target.get().getRole());
    }

    @Test
    void legitimateUsersExist() {
        List<User> legitUsers = userRepository.findByRole("LEGITIMATE");
        assertEquals(5, legitUsers.size());
        for (int i = 1; i <= 5; i++) {
            String username = "legit_user_" + i;
            Optional<User> user = userRepository.findByUsername(username);
            assertTrue(user.isPresent(), "User " + username + " should exist");
            assertTrue(user.get().isLegitimate());
        }
    }

    @Test
    void correctPasswordVerifiesForTarget() {
        assertTrue(userRepository.verifyCredentials("lab_target", "target123"));
    }

    @Test
    void incorrectPasswordFailsForTarget() {
        assertFalse(userRepository.verifyCredentials("lab_target", "wrongpassword"));
    }

    @Test
    void correctPasswordVerifiesForLegitimateUsers() {
        for (int i = 1; i <= 5; i++) {
            String username = "legit_user_" + i;
            String password = "legit" + i;
            assertTrue(userRepository.verifyCredentials(username, password),
                    "Correct password should verify for " + username);
        }
    }

    @Test
    void passwordHashingIsDeterministic() {
        String password = "test123";
        String hash1 = PasswordUtil.hashPassword(password);
        String hash2 = PasswordUtil.hashPassword(password);
        // Hashes should be different due to random salt
        assertNotEquals(hash1, hash2, "Hashes should differ due to salt");
        // But both should verify
        assertTrue(PasswordUtil.verifyPassword(password, hash1));
        assertTrue(PasswordUtil.verifyPassword(password, hash2));
    }

    @Test
    void passwordVerificationRejectsWrongPassword() {
        String hash = PasswordUtil.hashPassword("correct");
        assertFalse(PasswordUtil.verifyPassword("wrong", hash));
    }

    @Test
    void userRepositoryReturnsEmptyForNonExistentUser() {
        assertTrue(userRepository.findByUsername("nonexistent").isEmpty());
        assertTrue(userRepository.findById(-1).isEmpty());
    }
}