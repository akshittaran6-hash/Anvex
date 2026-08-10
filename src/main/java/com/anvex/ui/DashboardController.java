package com.anvex.ui;

import com.anvex.util.AppConfig;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @FXML private Label serverStatusLabel;
    @FXML private Label defenseStatusLabel;
    @FXML private Label scenarioLabel;
    @FXML private Label runLabel;
    @FXML private VBox topologyContent;
    @FXML private Label totalAttemptsValue;
    @FXML private Label failedValue;
    @FXML private Label blockedValue;
    @FXML private Label successValue;
    @FXML private Label alertsValue;
    @FXML private Label legitSuccessValue;
    @FXML private Button startBaselineBtn;
    @FXML private Button runAttackBtn;
    @FXML private Button enableDefenseBtn;
    @FXML private Button runProtectedAttackBtn;
    @FXML private Button resetBtn;
    @FXML private Button compareBtn;
    @FXML private Button replayBtn;
    @FXML private Button exportBtn;
    @FXML private TextArea eventsTextArea;
    @FXML private Label footerLabel;

    @FXML
    public void initialize() {
        logger.info("DashboardController initialized");
        appendEvent("SYSTEM: ANVEX dashboard ready");
        appendEvent("SYSTEM: Server binding address: " + AppConfig.SERVER_HOST + ":" + AppConfig.SERVER_PORT);
        appendEvent("SYSTEM: Scenario 1 parameters loaded (attack=2000, lockout=5, correct@1899)");
        updateServerStatus(false);
        updateDefenseStatus(false);
        updateRunLabel("—");
    }

    @FXML
    private void handleStartBaseline() {
        appendEvent("ACTION: Starting baseline experiment (DEFENSE OFF)");
        updateServerStatus(true);
        disableButtons(startBaselineBtn);
        enableButtons(runAttackBtn);
        updateRunLabel("BASELINE-1");
    }

    @FXML
    private void handleRunAttack() {
        appendEvent("ATTACK: Launching brute-force attack against lab_target (2000 attempts)");
        disableButtons(runAttackBtn);
        enableButtons(enableDefenseBtn);
        simulateAttack(false);
    }

    @FXML
    private void handleEnableDefense() {
        appendEvent("DEFENSE: AccountLockoutDefense ENABLED (threshold=5)");
        updateDefenseStatus(true);
        disableButtons(enableDefenseBtn);
        enableButtons(runProtectedAttackBtn, resetBtn);
    }

    @FXML
    private void handleRunProtectedAttack() {
        appendEvent("ATTACK: Launching protected attack against lab_target (DEFENSE ON)");
        disableButtons(runProtectedAttackBtn);
        enableButtons(compareBtn, replayBtn, exportBtn);
        updateRunLabel("PROTECTED-1");
        simulateAttack(true);
    }

    @FXML
    private void handleReset() {
        appendEvent("SYSTEM: Resetting live runtime state");
        updateServerStatus(false);
        updateDefenseStatus(false);
        updateRunLabel("—");
        resetMetrics();
        enableButtons(startBaselineBtn);
        disableButtons(runAttackBtn, enableDefenseBtn, runProtectedAttackBtn, compareBtn, replayBtn, exportBtn);
    }

    @FXML
    private void handleCompare() {
        appendEvent("VIEW: Opening before/after comparison");
    }

    @FXML
    private void handleReplay() {
        appendEvent("VIEW: Opening incident replay timeline");
    }

    @FXML
    private void handleExport() {
        appendEvent("EXPORT: Generating HTML incident report");
    }

    private void simulateAttack(boolean defenseEnabled) {
        new Thread(() -> {
            try {
                int totalAttempts = AppConfig.ATTACK_SIZE;
                int correctIndex = AppConfig.CORRECT_PASSWORD_INDEX;
                int lockoutThreshold = AppConfig.LOCKOUT_THRESHOLD;
                
                java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger blocked = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger successes = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicInteger alerts = new java.util.concurrent.atomic.AtomicInteger(0);
                java.util.concurrent.atomic.AtomicBoolean accountLocked = new java.util.concurrent.atomic.AtomicBoolean(false);
                java.util.concurrent.atomic.AtomicBoolean compromised = new java.util.concurrent.atomic.AtomicBoolean(false);

                for (int i = 0; i < totalAttempts; i++) {
                    final int attemptNum = i + 1;
                    final boolean isCorrect = (i == correctIndex);
                    final boolean wouldBlock = defenseEnabled && accountLocked.get();

                    Platform.runLater(() -> {
                        totalAttemptsValue.setText(String.valueOf(attemptNum));
                        if (!wouldBlock) {
                            failedValue.setText(String.valueOf(failures.get() + (isCorrect ? 0 : 1)));
                        } else {
                            blockedValue.setText(String.valueOf(blocked.get() + 1));
                        }
                    });

                    if (wouldBlock) {
                        blocked.incrementAndGet();
                        appendEvent(String.format("[%d] BLOCKED - Account locked (attempt %d)", attemptNum, attemptNum));
                        Thread.sleep(1);
                        continue;
                    }

                    if (isCorrect) {
                        successes.incrementAndGet();
                        compromised.set(true);
                        appendEvent(String.format("[%d] SUCCESS - Correct password reached! ACCOUNT COMPROMISED", attemptNum));
                        Platform.runLater(() -> {
                            successValue.setText(String.valueOf(successes.get()));
                        });
                    } else {
                        int currentFailures = failures.incrementAndGet();
                        appendEvent(String.format("[%d] FAILURE - Invalid password", attemptNum));
                        
                        if (defenseEnabled && currentFailures == lockoutThreshold) {
                            accountLocked.set(true);
                            alerts.incrementAndGet();
                            appendEvent("ALERT: RepeatedFailedLoginRule triggered for lab_target (5 failures)");
                            appendEvent("DEFENSE: Account lab_target LOCKED");
                            Platform.runLater(() -> {
                                alertsValue.setText(String.valueOf(alerts.get()));
                                failedValue.setText(String.valueOf(currentFailures));
                            });
                        }
                    }

                    Thread.sleep(2);
                }

                Platform.runLater(() -> {
                    if (compromised.get()) {
                        appendEvent("RESULT: ACCOUNT COMPROMISED — Attack succeeded without defense");
                    } else {
                        appendEvent("RESULT: ACCOUNT PROTECTED — Defense blocked attacker");
                        successValue.setText("0");
                    }
                    enableButtons(compareBtn, replayBtn, exportBtn, resetBtn);
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                appendEvent("ERROR: Attack simulation interrupted");
            }
        }).start();
    }

    private void appendEvent(String message) {
        Platform.runLater(() -> {
            String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
            eventsTextArea.appendText(String.format("%s %s%n", timestamp, message));
            eventsTextArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void updateServerStatus(boolean online) {
        Platform.runLater(() -> {
            serverStatusLabel.setText("SERVER: " + (online ? "ONLINE" : "OFFLINE"));
            serverStatusLabel.getStyleClass().removeAll("online", "offline");
            serverStatusLabel.getStyleClass().add(online ? "online" : "offline");
        });
    }

    private void updateDefenseStatus(boolean enabled) {
        Platform.runLater(() -> {
            defenseStatusLabel.setText("DEFENSE: " + (enabled ? "ON" : "OFF"));
            defenseStatusLabel.getStyleClass().removeAll("online", "offline", "active");
            defenseStatusLabel.getStyleClass().add(enabled ? "active" : "offline");
        });
    }

    private void updateRunLabel(String runId) {
        Platform.runLater(() -> runLabel.setText("RUN: " + runId));
    }

    private void resetMetrics() {
        Platform.runLater(() -> {
            totalAttemptsValue.setText("0");
            failedValue.setText("0");
            blockedValue.setText("0");
            successValue.setText("0");
            alertsValue.setText("0");
            legitSuccessValue.setText("0");
            eventsTextArea.clear();
        });
    }

    private void enableButtons(Button... buttons) {
        Platform.runLater(() -> {
            for (Button btn : buttons) {
                if (btn != null) btn.setDisable(false);
            }
        });
    }

    private void disableButtons(Button... buttons) {
        Platform.runLater(() -> {
            for (Button btn : buttons) {
                if (btn != null) btn.setDisable(true);
            }
        });
    }
}