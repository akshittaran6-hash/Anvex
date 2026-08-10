package com.anvex.attack;

import com.anvex.util.AppConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LoginAttackScenario {

    private final String targetUsername;
    private final List<String> passwords;

    public LoginAttackScenario(String targetUsername, String correctPassword) {
        if (targetUsername == null || targetUsername.isBlank()) {
            throw new IllegalArgumentException("Target username cannot be blank");
        }

        if (correctPassword == null || correctPassword.isBlank()) {
            throw new IllegalArgumentException("Correct password cannot be blank");
        }

        this.targetUsername = targetUsername;
        this.passwords = buildPasswordSequence(correctPassword);
    }

    private List<String> buildPasswordSequence(String correctPassword) {
        List<String> sequence = new ArrayList<>(AppConfig.ATTACK_SIZE);

        for (int i = 0; i < AppConfig.ATTACK_SIZE; i++) {
            if (i == AppConfig.CORRECT_PASSWORD_INDEX) {
                sequence.add(correctPassword);
            } else {
                sequence.add("wrong_password_" + i);
            }
        }

        return Collections.unmodifiableList(sequence);
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public int getAttemptCount() {
        return passwords.size();
    }

    public String getPassword(int index) {
        if (index < 0 || index >= passwords.size()) {
            throw new IndexOutOfBoundsException(
                    "Invalid attack attempt index: " + index
            );
        }

        return passwords.get(index);
    }

    public List<String> getPasswords() {
        return passwords;
    }
}
