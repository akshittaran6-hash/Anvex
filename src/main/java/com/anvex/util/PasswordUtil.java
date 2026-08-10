package com.anvex.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordUtil {

    private static final Logger logger = LoggerFactory.getLogger(PasswordUtil.class);
    private static final String ALGORITHM = "SHA-256";
    private static final int SALT_LENGTH = 16;
    private static final int ITERATIONS = 100000;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {}

    public static String hashPassword(String password) {
        try {
            byte[] salt = new byte[SALT_LENGTH];
            RANDOM.nextBytes(salt);

            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.reset();
            digest.update(salt);
            byte[] hash = digest.digest(password.getBytes());

            // Simple iteration for demonstration
            for (int i = 1; i < ITERATIONS; i++) {
                digest.reset();
                hash = digest.digest(hash);
            }

            byte[] combined = new byte[salt.length + hash.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(hash, 0, combined, salt.length, hash.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hashing algorithm not available", e);
        }
    }

    public static boolean verifyPassword(String password, String storedHash) {
        try {
            byte[] combined = Base64.getDecoder().decode(storedHash);
            if (combined.length < SALT_LENGTH) return false;

            byte[] salt = new byte[SALT_LENGTH];
            byte[] hash = new byte[combined.length - SALT_LENGTH];
            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
            System.arraycopy(combined, SALT_LENGTH, hash, 0, hash.length);

            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            digest.reset();
            digest.update(salt);
            byte[] testHash = digest.digest(password.getBytes());

            for (int i = 1; i < ITERATIONS; i++) {
                digest.reset();
                testHash = digest.digest(testHash);
            }

            return MessageDigest.isEqual(testHash, hash);
        } catch (Exception e) {
            logger.error("Password verification failed", e);
            return false;
        }
    }

    public static void main(String[] args) {
        // Demo password hashes for verification
        String[] passwords = {"target123", "legit1", "legit2", "legit3", "legit4", "legit5"};
        for (String pwd : passwords) {
            System.out.println(pwd + " -> " + hashPassword(pwd));
        }
    }
}