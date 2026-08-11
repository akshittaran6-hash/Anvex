package com.anvex.ui;

import com.anvex.util.AppConfig;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @FXML private Label serverStatusLabel;
    @FXML private Label defenseStatusLabel;
    @FXML private Label scenarioLabel;
    @FXML private Label runLabel;
    @FXML private HBox topologyContent;
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

    private final List<String> eventHistory = Collections.synchronizedList(new ArrayList<>());
    private volatile UiRunSnapshot baselineSnapshot;
    private volatile UiRunSnapshot protectedSnapshot;

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
        baselineSnapshot = null;
        protectedSnapshot = null;
        updateServerStatus(true);
        disableButtons(startBaselineBtn, runAttackBtn, enableDefenseBtn, runProtectedAttackBtn,
                compareBtn, replayBtn, exportBtn);
        updateRunLabel("BASELINE-1");
        enableButtons(runAttackBtn);
    }

    @FXML
    private void handleRunAttack() {
        appendEvent("ATTACK: Launching brute-force attack against lab_target (2000 attempts)");
        disableButtons(runAttackBtn, enableDefenseBtn);
        simulateAttack(false);
    }

    @FXML
    private void handleEnableDefense() {
        appendEvent("DEFENSE: AccountLockoutDefense ENABLED (threshold=5)");
        updateDefenseStatus(true);
        disableButtons(enableDefenseBtn, runProtectedAttackBtn);
        enableButtons(runProtectedAttackBtn, resetBtn);
    }

    @FXML
    private void handleRunProtectedAttack() {
        appendEvent("ATTACK: Launching protected attack against lab_target (DEFENSE ON)");
        disableButtons(runProtectedAttackBtn, compareBtn, replayBtn, exportBtn);
        updateRunLabel("PROTECTED-1");
        simulateAttack(true);
    }

    @FXML
    private void handleReset() {
        appendEvent("SYSTEM: Resetting live runtime state");
        updateServerStatus(false);
        updateDefenseStatus(false);
        updateRunLabel("—");
        baselineSnapshot = null;
        protectedSnapshot = null;
        resetMetrics();
        enableButtons(startBaselineBtn);
        disableButtons(runAttackBtn, enableDefenseBtn, runProtectedAttackBtn, compareBtn, replayBtn, exportBtn);
    }

    @FXML
    private void handleCompare() {
        if (baselineSnapshot == null || protectedSnapshot == null) {
            showInfo("Comparison unavailable", "Run both the baseline and protected experiments first.");
            return;
        }

        appendEvent("VIEW: Opening before/after comparison");
        showComparisonWindow();
    }

    @FXML
    private void handleReplay() {
        List<String> snapshot;
        synchronized (eventHistory) {
            snapshot = new ArrayList<>(eventHistory);
        }

        if (snapshot.isEmpty()) {
            showInfo("Replay unavailable", "No security events have been recorded yet.");
            return;
        }

        appendEvent("VIEW: Opening incident replay timeline");
        showReplayWindow(snapshot);
    }

    @FXML
    private void handleExport() {
        appendEvent("EXPORT: Generating HTML incident report");
        showInfo("Report export", "The report export hook is ready. The next step is wiring it to the persisted run data.");
    }

    private void simulateAttack(boolean defenseEnabled) {
        new Thread(() -> {
            long startTime = System.currentTimeMillis();
            try {
                int totalAttempts = AppConfig.ATTACK_SIZE;
                int correctIndex = AppConfig.CORRECT_PASSWORD_INDEX;
                int lockoutThreshold = AppConfig.LOCKOUT_THRESHOLD;

                AtomicInteger failures = new AtomicInteger(0);
                AtomicInteger blocked = new AtomicInteger(0);
                AtomicInteger successes = new AtomicInteger(0);
                AtomicInteger alerts = new AtomicInteger(0);
                AtomicBoolean accountLocked = new AtomicBoolean(false);
                AtomicBoolean compromised = new AtomicBoolean(false);

                for (int i = 0; i < totalAttempts; i++) {
                    int attemptNum = i + 1;
                    boolean isCorrect = (i == correctIndex);
                    boolean wouldBlock = defenseEnabled && accountLocked.get();

                    if (wouldBlock) {
                        blocked.incrementAndGet();
                        appendEvent(String.format("[%d] BLOCKED - Account locked (attempt %d)", attemptNum, attemptNum));
                    } else if (isCorrect) {
                        successes.incrementAndGet();
                        compromised.set(true);
                        appendEvent(String.format("[%d] SUCCESS - Correct password reached! ACCOUNT COMPROMISED", attemptNum));
                    } else {
                        int currentFailures = failures.incrementAndGet();
                        appendEvent(String.format("[%d] FAILURE - Invalid password", attemptNum));

                        if (defenseEnabled && currentFailures == lockoutThreshold) {
                            accountLocked.set(true);
                            alerts.incrementAndGet();
                            appendEvent("ALERT: RepeatedFailedLoginRule triggered for lab_target (5 failures)");
                            appendEvent("DEFENSE: Account lab_target LOCKED");
                        }
                    }

                    int currentFailures = failures.get();
                    int currentBlocked = blocked.get();
                    int currentSuccesses = successes.get();
                    int currentAlerts = alerts.get();
                    Platform.runLater(() -> {
                        totalAttemptsValue.setText(String.valueOf(attemptNum));
                        failedValue.setText(String.valueOf(currentFailures));
                        blockedValue.setText(String.valueOf(currentBlocked));
                        successValue.setText(String.valueOf(currentSuccesses));
                        alertsValue.setText(String.valueOf(currentAlerts));
                    });

                    Thread.sleep(2);
                }

                long duration = System.currentTimeMillis() - startTime;
                UiRunSnapshot snapshot = new UiRunSnapshot(
                        totalAttempts,
                        failures.get(),
                        blocked.get(),
                        successes.get(),
                        alerts.get(),
                        compromised.get(),
                        duration
                );

                if (defenseEnabled) {
                    protectedSnapshot = snapshot;
                } else {
                    baselineSnapshot = snapshot;
                }

                Platform.runLater(() -> {
                    if (compromised.get()) {
                        appendEvent("RESULT: ACCOUNT COMPROMISED — Attack succeeded without defense");
                    } else {
                        appendEvent("RESULT: ACCOUNT PROTECTED — Defense blocked attacker");
                    }

                    if (defenseEnabled) {
                        enableButtons(compareBtn, replayBtn, exportBtn, resetBtn);
                    } else {
                        enableButtons(enableDefenseBtn, resetBtn);
                    }
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                appendEvent("ERROR: Attack simulation interrupted");
            }
        }, defenseEnabled ? "anvex-protected-attack" : "anvex-baseline-attack").start();
    }

    private void showComparisonWindow() {
        UiRunSnapshot before = baselineSnapshot;
        UiRunSnapshot after = protectedSnapshot;

        VBox root = new VBox(16);
        root.getStyleClass().add("modal-root");
        root.setPadding(new Insets(22));

        HBox heading = new HBox(12);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(3);
        Label title = new Label("BEFORE / AFTER ANALYSIS");
        title.getStyleClass().add("modal-title");
        Label subtitle = new Label("Scenario 1 • controlled brute-force experiment");
        subtitle.getStyleClass().add("modal-subtitle");
        titleBox.getChildren().addAll(title, subtitle);
        RegionSpacer spacer = new RegionSpacer();
        Label resultBadge = new Label(after.compromised() ? "DEFENSE FAILED" : "DEFENSE EFFECTIVE");
        resultBadge.getStyleClass().add(after.compromised() ? "result-badge-danger" : "result-badge-success");
        HBox.setHgrow(spacer, Priority.ALWAYS);
        heading.getChildren().addAll(titleBox, spacer, resultBadge);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("comparison-grid");
        grid.setHgap(1);
        grid.setVgap(1);
        grid.setPadding(new Insets(1));

        addComparisonHeader(grid);
        addComparisonRow(grid, 1, "Total attempts", before.totalAttempts(), after.totalAttempts(), "fixed");
        addComparisonRow(grid, 2, "Failures", before.failures(), after.failures(), formatDelta(before.failures(), after.failures()));
        addComparisonRow(grid, 3, "Blocked", before.blocked(), after.blocked(), formatDelta(after.blocked(), before.blocked()));
        addComparisonRow(grid, 4, "Attacker success", before.successes(), after.successes(), formatDelta(after.successes(), before.successes()));
        addComparisonRow(grid, 5, "Alerts", before.alerts(), after.alerts(), formatDelta(after.alerts(), before.alerts()));
        addComparisonRow(grid, 6, "Duration", before.durationMs() + " ms", after.durationMs() + " ms", formatDelta(after.durationMs(), before.durationMs()) + " ms");
        addComparisonRow(grid, 7, "Account compromised", before.compromised() ? "YES" : "NO", after.compromised() ? "YES" : "NO",
                before.compromised() && !after.compromised() ? "PREVENTED" : "UNCHANGED");

        HBox insight = new HBox(12);
        insight.getStyleClass().add(after.compromised() ? "insight-danger" : "insight-success");
        insight.setPadding(new Insets(14));
        Label insightText = new Label(after.compromised()
                ? "The protected run still resulted in attacker success. Review the event replay and defense configuration."
                : "The defense changed the outcome: the baseline reached the correct password, while the protected run locked the account after repeated failures.");
        insightText.setWrapText(true);
        insightText.getStyleClass().add("insight-text");
        insight.getChildren().add(insightText);

        Button close = new Button("CLOSE");
        close.getStyleClass().add("btn-secondary");
        HBox closeBar = new HBox(close);
        closeBar.setAlignment(Pos.CENTER_RIGHT);
        close.setOnAction(e -> ((Stage) close.getScene().getWindow()).close());

        root.getChildren().addAll(heading, grid, insight, closeBar);
        showWindow("ANVEX • Compare Runs", root, 760, 560);
    }

    private void addComparisonHeader(GridPane grid) {
        addGridLabel(grid, "METRIC", 0, 0, "grid-header");
        addGridLabel(grid, "DEFENSE OFF", 1, 0, "grid-header");
        addGridLabel(grid, "DEFENSE ON", 2, 0, "grid-header");
        addGridLabel(grid, "CHANGE", 3, 0, "grid-header");
    }

    private void addComparisonRow(GridPane grid, int row, String metric, Object before, Object after, String change) {
        addGridLabel(grid, metric, 0, row, "grid-metric");
        addGridLabel(grid, String.valueOf(before), 1, row, "grid-value");
        addGridLabel(grid, String.valueOf(after), 2, row, "grid-value");
        addGridLabel(grid, change, 3, row, "grid-change");
    }

    private void addGridLabel(GridPane grid, String text, int column, int row, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setPadding(new Insets(11, 12, 11, 12));
        grid.add(label, column, row);
        if (column == 1 || column == 2) {
            GridPane.setHalignment(label, javafx.geometry.HPos.RIGHT);
        }
    }

    private void showReplayWindow(List<String> events) {
        ObservableList<String> allEvents = FXCollections.observableArrayList(events);
        ListView<String> listView = new ListView<>(allEvents);
        listView.getStyleClass().add("replay-list");
        listView.setFixedCellSize(28);
        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("replay-system", "replay-failure", "replay-blocked", "replay-success", "replay-alert");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item);
                if (item.contains("BLOCKED")) {
                    getStyleClass().add("replay-blocked");
                } else if (item.contains("FAILURE")) {
                    getStyleClass().add("replay-failure");
                } else if (item.contains("SUCCESS")) {
                    getStyleClass().add("replay-success");
                } else if (item.contains("ALERT") || item.contains("LOCKED")) {
                    getStyleClass().add("replay-alert");
                } else {
                    getStyleClass().add("replay-system");
                }
            }
        });

        TextField filter = new TextField();
        filter.setPromptText("Filter events: FAILURE, BLOCKED, ALERT, SUCCESS...");
        filter.getStyleClass().add("replay-filter");
        filter.textProperty().addListener((obs, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) {
                listView.setItems(allEvents);
                return;
            }
            ObservableList<String> filtered = FXCollections.observableArrayList();
            for (String event : allEvents) {
                if (event.toLowerCase(Locale.ROOT).contains(query)) {
                    filtered.add(event);
                }
            }
            listView.setItems(filtered);
        });

        Label count = new Label(events.size() + " events captured");
        count.getStyleClass().add("modal-subtitle");
        HBox toolbar = new HBox(10, filter, count);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(filter, Priority.ALWAYS);

        Button latest = new Button("LATEST EVENT");
        latest.getStyleClass().add("btn-info");
        latest.setOnAction(e -> {
            if (!listView.getItems().isEmpty()) {
                listView.scrollTo(listView.getItems().size() - 1);
                listView.getSelectionModel().selectLast();
            }
        });

        Button close = new Button("CLOSE");
        close.getStyleClass().add("btn-secondary");
        close.setOnAction(e -> ((Stage) close.getScene().getWindow()).close());

        HBox actions = new HBox(8, latest, close);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12);
        root.getStyleClass().add("modal-root");
        root.setPadding(new Insets(20));
        Label title = new Label("INCIDENT REPLAY TIMELINE");
        title.getStyleClass().add("modal-title");
        Label subtitle = new Label("Canonical UI event history • newest events appear at the bottom");
        subtitle.getStyleClass().add("modal-subtitle");
        VBox heading = new VBox(3, title, subtitle);
        VBox.setVgrow(listView, Priority.ALWAYS);
        root.getChildren().addAll(heading, toolbar, listView, actions);

        showWindow("ANVEX • Incident Replay", root, 920, 650);
    }

    private void showWindow(String title, VBox content, double width, double height) {
        Stage stage = new Stage();
        Window owner = startBaselineBtn.getScene() == null ? null : startBaselineBtn.getScene().getWindow();
        if (owner != null) {
            stage.initOwner(owner);
            stage.initModality(Modality.WINDOW_MODAL);
        }

        Scene scene = new Scene(content, width, height);
        var css = getClass().getResource("/css/dashboard.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setTitle(title);
        stage.setScene(scene);
        stage.setMinWidth(680);
        stage.setMinHeight(480);
        stage.show();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle("ANVEX");
        alert.setHeaderText(title);
        if (startBaselineBtn.getScene() != null) {
            alert.initOwner(startBaselineBtn.getScene().getWindow());
        }
        alert.showAndWait();
    }

    private String formatDelta(long newer, long older) {
        long delta = newer - older;
        if (delta == 0) return "—";
        return delta > 0 ? "+" + delta : String.valueOf(delta);
    }

    private void appendEvent(String message) {
        String timestamp = java.time.LocalTime.now().toString().substring(0, 8);
        String line = String.format("%s %s", timestamp, message);
        eventHistory.add(line);
        Platform.runLater(() -> {
            eventsTextArea.appendText(line + System.lineSeparator());
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
        eventHistory.clear();
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

    private record UiRunSnapshot(
            long totalAttempts,
            long failures,
            long blocked,
            long successes,
            long alerts,
            boolean compromised,
            long durationMs
    ) {}

    private static final class RegionSpacer extends javafx.scene.layout.Region {
    }
}
