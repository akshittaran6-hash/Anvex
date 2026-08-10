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
        System.out.println(">>> TEST: Calling resetInstance()");
        DatabaseManager.resetInstance();
        System.out.println(">>> TEST: Calling getTestInstance()");
        dbManager = DatabaseManager.getTestInstance();
        System.out.println(">>> TEST: Got dbManager, calling initialize()");
        dbManager.initialize();
        System.out.println(">>> TEST: initialize() completed");
        userRepository = new UserRepository(dbManager);

        System.out.println(">>> TEST: Inserting demo users...");
        insertDemoUsers();
        System.out.println(">>> TEST: Demo users inserted");
        
        List<User> users = userRepository.findAll();
        System.out.println(">>> TEST: Users in DB after insert: " + users.size());
        for (User u : users) {
            System.out.println(">>> TEST:   User: " + u.getUsername() + " role=" + u.getRole());
        }
    }

    @AfterAll
    static void tearDown() {
        DatabaseManager.resetInstance();
    }

    private static void insertDemoUsers() {
        String targetHash = PasswordUtil.hashPassword("target123");
        System.out.println(">>> TEST: Creating target user with hash: " + targetHash);
        userRepository.createUser("lab_target", targetHash, "TARGET");
        System.out.println(">>> TEST: Target user created");

        for (int i = 1; i <= 5; i++) {
            String username = "legit_user_" + i;
            String password = "legit" + i;
            String hash = PasswordUtil.hashPassword(password);
            System.out.println(">>> TEST: Creating legit user " + username + " with hash: " + hash);
            userRepository.createUser(username, hash, "LEGITIMATE");
            System.out.println(">>> TEST: Legit user " + username + " created");
        }
    }

    @Test
    void databaseInitializesWithDemoUsers() {
        System.out.println(">>> TEST: Running databaseInitializesWithDemoUsers");
        List<User> users = userRepository.findAll();
        System.out.println(">>> TEST: Users found: " + users.size());
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
        assertNotEquals(hash1, hash2, "Hashes should differ due to salt");
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
