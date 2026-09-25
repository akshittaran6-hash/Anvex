package com.anvex.protection;

public final class ProtectionConfig {

    private volatile long blockCooldownMs = 60_000;
    private volatile long highDelayMs = 3_000;
    private volatile long reaperIntervalMs = 5_000;

    public long getBlockCooldownMs() { return blockCooldownMs; }
    public long getHighDelayMs() { return highDelayMs; }
    public long getReaperIntervalMs() { return reaperIntervalMs; }

    public void setBlockCooldownMs(long blockCooldownMs) {
        if (blockCooldownMs <= 0) throw new IllegalArgumentException("Block cooldown must be positive");
        this.blockCooldownMs = blockCooldownMs;
    }

    public void setHighDelayMs(long highDelayMs) {
        if (highDelayMs < 0) throw new IllegalArgumentException("Delay cannot be negative");
        this.highDelayMs = highDelayMs;
    }

    public void setReaperIntervalMs(long reaperIntervalMs) {
        if (reaperIntervalMs <= 0) throw new IllegalArgumentException("Reaper interval must be positive");
        this.reaperIntervalMs = reaperIntervalMs;
    }
}
