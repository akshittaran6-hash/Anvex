package com.anvex.detection;

import com.anvex.event.Evidence;
import com.anvex.event.EventBus;
import com.anvex.event.EventListener;
import com.anvex.event.SecurityEvent;
import com.anvex.event.SecurityEventType;
import com.anvex.event.ThreatLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Map;

public final class DetectionEngine implements EventListener {

    private static final Logger logger = LoggerFactory.getLogger(DetectionEngine.class);

    private final DetectionConfig config;
    private final SourceActivityTracker tracker;
    private final EventBus eventBus;
    private final ConcurrentMap<String, ThreatLevel> currentLevels = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> storedScores = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Integer> decayBaseScores = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Instant> lastActivityTimes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> sourceRunIds = new ConcurrentHashMap<>();

    public DetectionEngine(DetectionConfig config, SourceActivityTracker tracker, EventBus eventBus) {
        this.config = config;
        this.tracker = tracker;
        this.eventBus = eventBus;
    }

    @Override
    public void onEvent(SecurityEvent event) {
        if (!isLoginEvent(event.getEventType())) {
            return;
        }
        String sourceId = event.getSource();
        if (sourceId == null || sourceId.isEmpty()) {
            return;
        }

        AttemptOutcome outcome = mapOutcome(event.getOutcome());
        SourceActivityTracker.AttemptHistory history = tracker.getHistory(sourceId);
        synchronized (history) {
            tracker.recordAttempt(sourceId, event.getTimestamp(), outcome);
            sourceRunIds.put(sourceId, event.getRunId());
            ThreatAssessment assessment = evaluate(sourceId, event.getTimestamp(), event.getRunId());
            lastActivityTimes.put(sourceId, event.getTimestamp());
            decayBaseScores.put(sourceId, assessment.getThreatScore());
        }
    }

    public ThreatAssessment assess(String sourceId, Instant now) {
        SourceActivityTracker.AttemptHistory history = tracker.getHistory(sourceId);
        synchronized (history) {
            return evaluate(sourceId, now, sourceRunIds.getOrDefault(sourceId, 0L));
        }
    }

    public ThreatLevel getCurrentLevel(String sourceId) {
        return currentLevels.getOrDefault(sourceId, ThreatLevel.NORMAL);
    }

    public int getStoredScore(String sourceId) {
        return storedScores.getOrDefault(sourceId, 0);
    }


    public void reset() {
        currentLevels.clear();
        storedScores.clear();
        decayBaseScores.clear();
        lastActivityTimes.clear();
        sourceRunIds.clear();
        tracker.clear();
    }

    private ThreatAssessment evaluate(String sourceId, Instant now, long runId) {
        SourceActivityTracker.AttemptHistory history = tracker.getHistory(sourceId);

        long windowMs = config.getTimeWindowMs();
        int attemptsInWindow = history.countWithinWindow(windowMs, now);
        int failuresInWindow = history.failuresWithinWindow(windowMs, now);
        double frequency = computeFrequency(history, now, attemptsInWindow);
        int streak = attemptsInWindow == 0 ? 0 : history.getConsecutiveFailures();

        int freshScore = computeScore(history, now);
        int decayedScore = applyDecay(sourceId, now);
        int effectiveScore = Math.max(freshScore, decayedScore);

        ThreatLevel previousLevel = currentLevels.getOrDefault(sourceId, ThreatLevel.NORMAL);
        ThreatLevel effectiveLevel = bandOf(effectiveScore);

        boolean streakRule = attemptsInWindow > 0 && streak >= config.getCriticalStreakThreshold();
        if (streakRule) {
            effectiveScore = Math.max(effectiveScore, config.getHighBandLimit() + 1);
            effectiveLevel = ThreatLevel.CRITICAL;
        }

        if (effectiveLevel == ThreatLevel.NORMAL && attemptsInWindow == 0) {
            history.resetStreak();
        }

        boolean escalated = effectiveLevel.getSeverity() > previousLevel.getSeverity();
        boolean deescalated = effectiveLevel.getSeverity() < previousLevel.getSeverity();

        currentLevels.put(sourceId, effectiveLevel);
        storedScores.put(sourceId, effectiveScore);

        Evidence evidence = buildEvidence(failuresInWindow, windowMs, frequency, effectiveScore);
        ThreatAssessment assessment = new ThreatAssessment(sourceId, effectiveLevel, effectiveScore, evidence, now,
                escalated, streakRule ? "failure-streak" : "weighted-score");

        if (escalated) {
            if (previousLevel == ThreatLevel.NORMAL && effectiveLevel.isAtLeast(ThreatLevel.SUSPICIOUS)) {
                publishDecisionEvent(SecurityEventType.ANOMALY_DETECTED, assessment, runId,
                        String.format("Anomalous behaviour detected from %s", sourceId));
            }
            if (effectiveLevel == ThreatLevel.HIGH || effectiveLevel == ThreatLevel.CRITICAL) {
                publishDecisionEvent(SecurityEventType.THREAT_ESCALATED, assessment, runId,
                        String.format("Threat escalated to %s for %s (score %d)", effectiveLevel, sourceId, effectiveScore));
            }
        } else if (deescalated) {
            publishDecisionEvent(SecurityEventType.THREAT_DEESCALATED, assessment, runId,
                    String.format("Threat de-escalated to %s for %s (score %d)", effectiveLevel, sourceId, effectiveScore));
        }

        return assessment;
    }

    private Evidence buildEvidence(int failuresInWindow, long windowMs, double frequency, int effectiveScore) {
        return Evidence.builder()
                .failedAttemptCount(failuresInWindow)
                .timeWindowMs(windowMs)
                .requestFrequency(frequency)
                .thresholdExceeded(effectiveScore > config.getNormalBandLimit())
                .build();
    }

    private double computeFrequency(SourceActivityTracker.AttemptHistory history, Instant now, int attemptsInWindow) {
        if (attemptsInWindow <= 0) {
            return 0.0;
        }
        long windowMs = config.getTimeWindowMs();
        Instant first = history.getFirstActivity();
        Instant last = history.getLastActivity();
        double spanSeconds = 0.0;
        if (first != null && last != null) {
            spanSeconds = (last.toEpochMilli() - first.toEpochMilli()) / 1000.0;
        }
        if (spanSeconds > windowMs / 1000.0) {
            spanSeconds = windowMs / 1000.0;
        }
        return attemptsInWindow / Math.max(2.0, spanSeconds);
    }

    private int computeScore(SourceActivityTracker.AttemptHistory history, Instant now) {
        long windowMs = config.getTimeWindowMs();
        int attemptsInWindow = history.countWithinWindow(windowMs, now);
        int failuresInWindow = history.failuresWithinWindow(windowMs, now);
        double frequency = computeFrequency(history, now, attemptsInWindow);
        int streak = attemptsInWindow == 0 ? 0 : history.getConsecutiveFailures();

        int failureStep = Math.max(1, config.getMaxFailurePoints() / config.getFailureSaturationCount());
        int failureScore = Math.min(config.getMaxFailurePoints(), failuresInWindow * failureStep);

        double frequencyStep = config.getMaxFrequencyPoints() / config.getFrequencySaturationRate();
        int frequencyScore = (int) Math.min(config.getMaxFrequencyPoints(), frequency * frequencyStep);

        int densityStep = Math.max(1, config.getMaxDensityPoints() / config.getDensitySaturationCount());
        int densityScore = Math.min(config.getMaxDensityPoints(), attemptsInWindow * densityStep);

        int patternStep = Math.max(1, config.getMaxPatternPoints() / config.getPatternSaturationStreak());
        int patternScore = Math.min(config.getMaxPatternPoints(), streak * patternStep);

        return Math.min(100, failureScore + frequencyScore + densityScore + patternScore);
    }

    private int applyDecay(String sourceId, Instant now) {
        Integer storedScore = decayBaseScores.get(sourceId);
        Instant lastActivity = lastActivityTimes.get(sourceId);
        if (storedScore == null || storedScore <= 0 || lastActivity == null) {
            return 0;
        }

        long elapsedMs = now.toEpochMilli() - lastActivity.toEpochMilli();
        if (elapsedMs <= 0) {
            return storedScore;
        }

        long periods = elapsedMs / config.getDecayIntervalMs();
        int decayed = storedScore - (int) periods * config.getDecayStepPerInterval();
        return Math.max(0, decayed);
    }

    private ThreatLevel bandOf(int score) {
        if (score <= config.getNormalBandLimit()) return ThreatLevel.NORMAL;
        if (score <= config.getSuspiciousBandLimit()) return ThreatLevel.SUSPICIOUS;
        if (score <= config.getHighBandLimit()) return ThreatLevel.HIGH;
        return ThreatLevel.CRITICAL;
    }

    private void publishDecisionEvent(SecurityEventType eventType, ThreatAssessment assessment,
                                      long runId, String message) {
        eventBus.publish(SecurityEvent.builder()
                .runId(runId)
                .eventType(eventType)
                .source(assessment.getSourceId())
                .outcome(assessment.getThreatLevel().name())
                .message(message)
                .threatLevel(assessment.getThreatLevel())
                .threatScore(assessment.getThreatScore())
                .metadata(Map.of("reason", assessment.getReason()))
                .evidence(assessment.getEvidence())
                .build());
        logger.info("{}: {}", eventType, message);
    }

    private boolean isLoginEvent(SecurityEventType eventType) {
        return eventType == SecurityEventType.LOGIN_FAILURE
                || eventType == SecurityEventType.LOGIN_SUCCESS
                || eventType == SecurityEventType.LOGIN_BLOCKED;
    }

    private AttemptOutcome mapOutcome(String outcome) {
        if ("SUCCESS".equals(outcome)) return AttemptOutcome.SUCCESS;
        if ("BLOCKED".equals(outcome)) return AttemptOutcome.BLOCKED;
        return AttemptOutcome.FAILURE;
    }
}
