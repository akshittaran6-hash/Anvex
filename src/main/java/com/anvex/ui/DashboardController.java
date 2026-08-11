package com.anvex.ui;

import com.anvex.util.AppConfig;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
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
    private volatile boolean attackRunning;

    @FXML
    public void initialize() {
        logger.info("DashboardController initialized");
        appendEvent("SYSTEM: ANVEX dashboard ready");
        appendEvent("SYSTEM: Server binding address: " + AppConfig.SERVER_HOST + ":" + AppConfig.SERVER_PORT);
        appendEvent("SYSTEM: Scenario 1 loaded • 2000 attempts • correct password @ attempt 1900 • lockout threshold 5");
        updateServerStatus(false);
        updateDefenseStatus(false);
        updateRunLabel("—");
    }

    @FXML
    private void handleStartBaseline() {
        if (attackRunning) return;
        baselineSnapshot = null;
        protectedSnapshot = null;
        clearMetrics();
        appendEvent("ACTION: Starting baseline experiment • DEFENSE OFF");
        updateServerStatus(true);
        updateDefenseStatus(false);
        updateRunLabel("BASELINE-1");
        disableButtons(startBaselineBtn, runAttackBtn, enableDefenseBtn, runProtectedAttackBtn, compareBtn, replayBtn, exportBtn);
        enableButtons(runAttackBtn, resetBtn);
    }

    @FXML
    private void handleRunAttack() {
        if (attackRunning) return;
        appendEvent("ATTACK: Launching brute-force attack • 2000 ordered requests");
        disableButtons(runAttackBtn, enableDefenseBtn);
        simulateAttack(false);
    }

    @FXML
    private void handleEnableDefense() {
        if (attackRunning) return;
        appendEvent("DEFENSE: AccountLockoutDefense ENABLED • threshold=5");
        updateDefenseStatus(true);
        disableButtons(enableDefenseBtn, runProtectedAttackBtn);
        enableButtons(runProtectedAttackBtn, resetBtn);
    }

    @FXML
    private void handleRunProtectedAttack() {
        if (attackRunning) return;
        appendEvent("ATTACK: Launching protected brute-force attack • DEFENSE ON");
        updateRunLabel("PROTECTED-1");
        disableButtons(runProtectedAttackBtn, compareBtn, replayBtn, exportBtn);
        simulateAttack(true);
    }

    @FXML
    private void handleReset() {
        if (attackRunning) return;
        baselineSnapshot = null;
        protectedSnapshot = null;
        clearMetrics();
        synchronized (eventHistory) { eventHistory.clear(); }
        eventsTextArea.clear();
        appendEvent("SYSTEM: Runtime reset • ready for a new experiment");
        updateServerStatus(false);
        updateDefenseStatus(false);
        updateRunLabel("—");
        disableButtons(runAttackBtn, enableDefenseBtn, runProtectedAttackBtn, compareBtn, replayBtn, exportBtn);
        enableButtons(startBaselineBtn, resetBtn);
    }

    @FXML
    private void handleCompare() {
        if (baselineSnapshot == null || protectedSnapshot == null) {
            showInfo("Comparison unavailable", "Complete both the baseline and protected runs first.");
            return;
        }
        appendEvent("VIEW: Opening before/after comparison");
        showComparisonWindow(baselineSnapshot, protectedSnapshot);
    }

    @FXML
    private void handleReplay() {
        List<String> snapshot;
        synchronized (eventHistory) { snapshot = new ArrayList<>(eventHistory); }
        if (snapshot.isEmpty()) {
            showInfo("Replay unavailable", "No events have been recorded yet.");
            return;
        }
        appendEvent("VIEW: Opening incident replay timeline");
        synchronized (eventHistory) { snapshot = new ArrayList<>(eventHistory); }
        showReplayWindow(snapshot);
    }

    @FXML
    private void handleExport() {
        appendEvent("EXPORT: Report requested");
        showInfo("Report export", "The experiment data is captured in the event stream. HTML export can be wired to the persisted run repositories next.");
    }

    private void simulateAttack(boolean defenseEnabled) {
        attackRunning = true;
        Thread worker = new Thread(() -> {
            long start = System.currentTimeMillis();
            AtomicInteger failures = new AtomicInteger();
            AtomicInteger blocked = new AtomicInteger();
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger alerts = new AtomicInteger();
            AtomicBoolean locked = new AtomicBoolean();
            AtomicBoolean compromised = new AtomicBoolean();

            try {
                for (int i = 0; i < AppConfig.ATTACK_SIZE; i++) {
                    int attempt = i + 1;
                    boolean correct = i == AppConfig.CORRECT_PASSWORD_INDEX;

                    if (defenseEnabled && locked.get()) {
                        blocked.incrementAndGet();
                        appendEvent(String.format("[%04d] BLOCKED  account locked", attempt));
                    } else if (correct) {
                        successes.incrementAndGet();
                        compromised.set(true);
                        appendEvent(String.format("[%04d] SUCCESS  correct password reached • ACCOUNT COMPROMISED", attempt));
                    } else {
                        int failureCount = failures.incrementAndGet();
                        appendEvent(String.format("[%04d] FAILURE  invalid credentials", attempt));
                        if (defenseEnabled && failureCount == AppConfig.LOCKOUT_THRESHOLD) {
                            locked.set(true);
                            alerts.incrementAndGet();
                            appendEvent("[ALERT] RepeatedFailedLoginRule triggered • 5 failures");
                            appendEvent("[DEFENSE] lab_target LOCKED • remaining attacker requests will be blocked");
                        }
                    }

                    int f = failures.get();
                    int b = blocked.get();
                    int s = successes.get();
                    int a = alerts.get();
                    Platform.runLater(() -> updateMetrics(attempt, f, b, s, a));
                    Thread.sleep(2);
                }

                long duration = System.currentTimeMillis() - start;
                UiRunSnapshot snapshot = new UiRunSnapshot(
                        AppConfig.ATTACK_SIZE, failures.get(), blocked.get(), successes.get(), alerts.get(), compromised.get(), duration);

                if (defenseEnabled) protectedSnapshot = snapshot;
                else baselineSnapshot = snapshot;

                Platform.runLater(() -> finishRun(defenseEnabled, compromised.get(), snapshot));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                appendEvent("ERROR: Attack simulation interrupted");
                Platform.runLater(() -> attackRunning = false);
            }
        }, defenseEnabled ? "anvex-protected-attack" : "anvex-baseline-attack");
        worker.setDaemon(true);
        worker.start();
    }

    private void finishRun(boolean defenseEnabled, boolean compromised, UiRunSnapshot snapshot) {
        attackRunning = false;
        if (compromised) {
            appendEvent("RESULT: BASELINE ATTACK SUCCEEDED • ACCOUNT COMPROMISED");
        } else {
            appendEvent("RESULT: PROTECTED ATTACK BLOCKED • ACCOUNT SAFE");
        }

        if (defenseEnabled) {
            enableButtons(compareBtn, replayBtn, exportBtn, resetBtn);
            disableButtons(runProtectedAttackBtn, enableDefenseBtn);
        } else {
            enableButtons(enableDefenseBtn, resetBtn);
            disableButtons(runAttackBtn);
        }
        updateServerStatus(true);
    }

    private void updateMetrics(int attempts, int failures, int blocked, int successes, int alerts) {
        totalAttemptsValue.setText(String.valueOf(attempts));
        failedValue.setText(String.valueOf(failures));
        blockedValue.setText(String.valueOf(blocked));
        successValue.setText(String.valueOf(successes));
        alertsValue.setText(String.valueOf(alerts));
    }

    private void showComparisonWindow(UiRunSnapshot before, UiRunSnapshot after) {
        VBox root = new VBox(16);
        root.getStyleClass().add("modal-root");
        root.setPadding(new Insets(22));

        HBox heading = new HBox(12);
        heading.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(3);
        Label title = new Label("BEFORE / AFTER ANALYSIS");
        title.getStyleClass().add("modal-title");
        Label subtitle = new Label("Scenario 1 • measured effect of account lockout defense");
        subtitle.getStyleClass().add("modal-subtitle");
        titleBox.getChildren().addAll(title, subtitle);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label(after.compromised() ? "DEFENSE FAILED" : "DEFENSE EFFECTIVE");
        badge.getStyleClass().add(after.compromised() ? "result-badge-danger" : "result-badge-success");
        heading.getChildren().addAll(titleBox, spacer, badge);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("comparison-grid");
        grid.setHgap(1);
        grid.setVgap(1);
        addGridLabel(grid, "METRIC", 0, 0, "grid-header");
        addGridLabel(grid, "DEFENSE OFF", 1, 0, "grid-header");
        addGridLabel(grid, "DEFENSE ON", 2, 0, "grid-header");
        addGridLabel(grid, "CHANGE", 3, 0, "grid-header");
        addComparisonRow(grid, 1, "Total attempts", before.totalAttempts(), after.totalAttempts(), "—");
        addComparisonRow(grid, 2, "Failures", before.failures(), after.failures(), delta(after.failures(), before.failures()));
        addComparisonRow(grid, 3, "Blocked", before.blocked(), after.blocked(), "+" + after.blocked());
        addComparisonRow(grid, 4, "Attacker success", before.successes(), after.successes(), delta(after.successes(), before.successes()));
        addComparisonRow(grid, 5, "Alerts", before.alerts(), after.alerts(), "+" + after.alerts());
        addComparisonRow(grid, 6, "Duration", before.durationMs() + " ms", after.durationMs() + " ms", delta(after.durationMs(), before.durationMs()) + " ms");
        addComparisonRow(grid, 7, "Account compromised", before.compromised() ? "YES" : "NO", after.compromised() ? "YES" : "NO", before.compromised() && !after.compromised() ? "PREVENTED" : "—");

        HBox insight = new HBox();
        insight.setPadding(new Insets(14));
        insight.getStyleClass().add(after.compromised() ? "insight-danger" : "insight-success");
        Label insightText = new Label(after.compromised()
                ? "The protected run still reached a successful attacker login. Review the defense configuration and replay."
                : "PROOF: the same ordered attack compromised the account with defense OFF, while defense ON locked the account after five failures and blocked the remaining requests.");
        insightText.setWrapText(true);
        insightText.getStyleClass().add("insight-text");
        insight.getChildren().add(insightText);

        Button close = new Button("CLOSE");
        close.getStyleClass().add("btn-secondary");
        close.setOnAction(e -> ((Stage) close.getScene().getWindow()).close());
        HBox actions = new HBox(close);
        actions.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().addAll(heading, grid, insight, actions);
        showWindow("ANVEX • Compare Runs", root, 820, 610);
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
    }

    private void showReplayWindow(List<String> events) {
        ObservableList<String> allEvents = FXCollections.observableArrayList(events);
        ListView<String> list = new ListView<>(allEvents);
        list.getStyleClass().add("replay-list");
        list.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("replay-failure", "replay-blocked", "replay-success", "replay-alert", "replay-system");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                if (item.contains("BLOCKED")) getStyleClass().add("replay-blocked");
                else if (item.contains("FAILURE")) getStyleClass().add("replay-failure");
                else if (item.contains("SUCCESS")) getStyleClass().add("replay-success");
                else if (item.contains("ALERT") || item.contains("LOCKED")) getStyleClass().add("replay-alert");
                else getStyleClass().add("replay-system");
            }
        });

        TextField filter = new TextField();
        filter.setPromptText("Filter: FAILURE, BLOCKED, ALERT, SUCCESS...");
        filter.getStyleClass().add("replay-filter");
        Label count = new Label(events.size() + " events captured");
        count.getStyleClass().add("modal-subtitle");
        HBox.setHgrow(filter, Priority.ALWAYS);
        HBox toolbar = new HBox(10, filter, count);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        filter.textProperty().addListener((obs, oldValue, newValue) -> {
            String q = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            if (q.isEmpty()) { list.setItems(allEvents); return; }
            ObservableList<String> filtered = FXCollections.observableArrayList();
            for (String event : allEvents) if (event.toLowerCase(Locale.ROOT).contains(q)) filtered.add(event);
            list.setItems(filtered);
        });

        Button latest = new Button("LATEST");
        latest.getStyleClass().add("btn-info");
        latest.setOnAction(e -> { if (!list.getItems().isEmpty()) list.scrollTo(list.getItems().size() - 1); });
        Button close = new Button("CLOSE");
        close.getStyleClass().add("btn-secondary");
        close.setOnAction(e -> ((Stage) close.getScene().getWindow()).close());
        HBox actions = new HBox(8, latest, close);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12);
        root.getStyleClass().add("modal-root");
        root.setPadding(new Insets(20));
        Label title = new Label("INCIDENT REPLAY");
        title.getStyleClass().add("modal-title");
        Label subtitle = new Label("Chronological security event stream • filterable local replay");
        subtitle.getStyleClass().add("modal-subtitle");
        VBox.setVgrow(list, Priority.ALWAYS);
        root.getChildren().addAll(title, subtitle, toolbar, list, actions);
        showWindow("ANVEX • Incident Replay", root, 900, 650);
    }

    private void showWindow(String title, VBox root, double width, double height) {
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        if (eventsTextArea.getScene() != null && eventsTextArea.getScene().getWindow() instanceof Stage owner) stage.initOwner(owner);
        Scene scene = new Scene(root, width, height);
        String css = getClass().getResource("/css/dashboard.css").toExternalForm();
        scene.getStylesheets().add(css);
        stage.setTitle(title);
        stage.setScene(scene);
        stage.show();
    }

    private void showInfo(String title, String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
        alert.setTitle("ANVEX");
        alert.setHeaderText(title);
        alert.setContentText(message);
        if (eventsTextArea.getScene() != null && eventsTextArea.getScene().getWindow() != null) alert.initOwner(eventsTextArea.getScene().getWindow());
        alert.showAndWait();
    }

    private void appendEvent(String message) {
        String timestamp = java.time.LocalTime.now().toString();
        if (timestamp.length() > 8) timestamp = timestamp.substring(0, 8);
        String line = timestamp + "  " + message;
        synchronized (eventHistory) { eventHistory.add(line); }
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

    private void clearMetrics() {
        totalAttemptsValue.setText("0");
        failedValue.setText("0");
        blockedValue.setText("0");
        successValue.setText("0");
        alertsValue.setText("0");
        legitSuccessValue.setText("0");
    }

    private void enableButtons(Button... buttons) {
        Platform.runLater(() -> { for (Button b : buttons) if (b != null) b.setDisable(false); });
    }

    private void disableButtons(Button... buttons) {
        Platform.runLater(() -> { for (Button b : buttons) if (b != null) b.setDisable(true); });
    }

    private static String delta(long after, long before) {
        long d = after - before;
        return d > 0 ? "+" + d : String.valueOf(d);
    }

    private record UiRunSnapshot(int totalAttempts, int failures, int blocked, int successes, int alerts, boolean compromised, long durationMs) {}
}
