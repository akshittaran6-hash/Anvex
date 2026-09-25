package com.anvex.detection;

public final class DetectionConfig {

    private volatile int normalBandLimit = 25;
    private volatile int suspiciousBandLimit = 50;
    private volatile int highBandLimit = 80;

    private volatile long timeWindowMs = 30_000;
    private volatile int maxFailurePoints = 40;
    private volatile int failureSaturationCount = 10;
    private volatile int maxFrequencyPoints = 30;
    private volatile double frequencySaturationRate = 10.0;
    private volatile int maxDensityPoints = 20;
    private volatile int densitySaturationCount = 20;
    private volatile int maxPatternPoints = 10;
    private volatile int patternSaturationStreak = 5;
    private volatile long decayIntervalMs = 10_000;
    private volatile int decayStepPerInterval = 15;
    private volatile int criticalStreakThreshold = 15;

    public int getNormalBandLimit() { return normalBandLimit; }
    public int getSuspiciousBandLimit() { return suspiciousBandLimit; }
    public int getHighBandLimit() { return highBandLimit; }
    public long getTimeWindowMs() { return timeWindowMs; }
    public int getMaxFailurePoints() { return maxFailurePoints; }
    public int getFailureSaturationCount() { return failureSaturationCount; }
    public int getMaxFrequencyPoints() { return maxFrequencyPoints; }
    public double getFrequencySaturationRate() { return frequencySaturationRate; }
    public int getMaxDensityPoints() { return maxDensityPoints; }
    public int getDensitySaturationCount() { return densitySaturationCount; }
    public int getMaxPatternPoints() { return maxPatternPoints; }
    public int getPatternSaturationStreak() { return patternSaturationStreak; }
    public long getDecayIntervalMs() { return decayIntervalMs; }
    public int getDecayStepPerInterval() { return decayStepPerInterval; }
    public int getCriticalStreakThreshold() { return criticalStreakThreshold; }

    public void setNormalBandLimit(int normalBandLimit) {
        if (normalBandLimit < 0 || normalBandLimit >= suspiciousBandLimit) {
            throw new IllegalArgumentException("Normal band limit must be in [0, suspiciousBandLimit)");
        }
        this.normalBandLimit = normalBandLimit;
    }

    public void setSuspiciousBandLimit(int suspiciousBandLimit) {
        if (suspiciousBandLimit <= normalBandLimit || suspiciousBandLimit >= highBandLimit) {
            throw new IllegalArgumentException("Suspicious band limit must be between normalBandLimit and highBandLimit");
        }
        this.suspiciousBandLimit = suspiciousBandLimit;
    }

    public void setHighBandLimit(int highBandLimit) {
        if (highBandLimit <= suspiciousBandLimit || highBandLimit >= 100) {
            throw new IllegalArgumentException("High band limit must be in (suspiciousBandLimit, 100)");
        }
        this.highBandLimit = highBandLimit;
    }

    public void setTimeWindowMs(long timeWindowMs) {
        if (timeWindowMs <= 0) {
            throw new IllegalArgumentException("Time window must be positive");
        }
        this.timeWindowMs = timeWindowMs;
    }

    public void setMaxFailurePoints(int maxFailurePoints) {
        if (maxFailurePoints < 0) throw new IllegalArgumentException("Points cannot be negative");
        this.maxFailurePoints = maxFailurePoints;
    }

    public void setFailureSaturationCount(int failureSaturationCount) {
        if (failureSaturationCount <= 0) throw new IllegalArgumentException("Saturation count must be positive");
        this.failureSaturationCount = failureSaturationCount;
    }

    public void setMaxFrequencyPoints(int maxFrequencyPoints) {
        if (maxFrequencyPoints < 0) throw new IllegalArgumentException("Points cannot be negative");
        this.maxFrequencyPoints = maxFrequencyPoints;
    }

    public void setFrequencySaturationRate(double frequencySaturationRate) {
        if (!Double.isFinite(frequencySaturationRate) || frequencySaturationRate <= 0)
            throw new IllegalArgumentException("Saturation rate must be finite and positive");
        this.frequencySaturationRate = frequencySaturationRate;
    }

    public void setMaxDensityPoints(int maxDensityPoints) {
        if (maxDensityPoints < 0) throw new IllegalArgumentException("Points cannot be negative");
        this.maxDensityPoints = maxDensityPoints;
    }

    public void setDensitySaturationCount(int densitySaturationCount) {
        if (densitySaturationCount <= 0) throw new IllegalArgumentException("Saturation count must be positive");
        this.densitySaturationCount = densitySaturationCount;
    }

    public void setMaxPatternPoints(int maxPatternPoints) {
        if (maxPatternPoints < 0) throw new IllegalArgumentException("Points cannot be negative");
        this.maxPatternPoints = maxPatternPoints;
    }

    public void setPatternSaturationStreak(int patternSaturationStreak) {
        if (patternSaturationStreak <= 0) throw new IllegalArgumentException("Saturation streak must be positive");
        this.patternSaturationStreak = patternSaturationStreak;
    }

    public void setDecayIntervalMs(long decayIntervalMs) {
        if (decayIntervalMs <= 0) throw new IllegalArgumentException("Decay interval must be positive");
        this.decayIntervalMs = decayIntervalMs;
    }

    public void setDecayStepPerInterval(int decayStepPerInterval) {
        if (decayStepPerInterval < 0) throw new IllegalArgumentException("Decay step cannot be negative");
        this.decayStepPerInterval = decayStepPerInterval;
    }

    public void setCriticalStreakThreshold(int criticalStreakThreshold) {
        if (criticalStreakThreshold <= 0) throw new IllegalArgumentException("Critical streak threshold must be positive");
        this.criticalStreakThreshold = criticalStreakThreshold;
    }
}
