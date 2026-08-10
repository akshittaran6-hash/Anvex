package com.anvex;

import com.anvex.persistence.DatabaseManager;
import com.anvex.persistence.User;
import com.anvex.persistence.UserRepository;
import com.anvex.util.PasswordUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseUsersTest {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseUsersTest.class);

    private static DatabaseManager dbManager;
    private static UserRepository userRepository;

    @BeforeAll
    static void setUp() throws SQLException {
        DatabaseManager.resetInstance();
        dbManager = DatabaseManager.getTestInstance();
        logger.info("Calling dbManager.initialize()...");
        dbManager.initialize();
        logger.info("dbManager.initialize() completed");
        userRepository = new UserRepository(dbManager);

        // Insert demo users with proper password hashes
        logger.info("Inserting demo users...");
        insertDemoUsers();
        logger.info("Demo users inserted");
        
        // Verify immediately
        List<User> users = userRepository.findAll();
        logger.info("Users in DB after insert: {}", users.size());
        for (User u : users) {
            logger.info("  User: {} role={}", u.getUsername(), u.getRole());
        }
    }

    @AfterAll
    static void tearDown() {
        DatabaseManager.resetInstance();
    }

    private static void insertDemoUsers() {
        // Target account
        String targetHash = PasswordUtil.hashPassword("target123");
        logger.info("Creating target user with hash: {}", targetHash);
        userRepository.createUser("lab_target", targetHash, "TARGET");
        logger.info("Target user created");

        // Legitimate users
        for (int i = 1; i <= 5; i++) {
            String username = "legit_user_" + i;
            String password = "legit" + i;
            String hash = PasswordUtil.hashPassword(password);
            logger.info("Creating legit user {} with hash: {}", username, hash);
            userRepository.createUser(username, hash, "LEGITIMATE");
            logger.info("Legit user {} created", username);
        }
    }

    @Test
    void databaseInitializesWithDemoUsers() {
        System.out.println(">>> RUNNING NEW TEST CODE <<<");
        List<User> users = userRepository.findAll();
        System.out.println(">>> Users found: " + users.size());
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