package com.anvex.detection;

import java.time.Instant;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;

public final class SourceActivityTracker {

    private final ConcurrentMap<String, AttemptHistory> sources = new ConcurrentHashMap<>();

    public void recordAttempt(String sourceId, Instant timestamp, AttemptOutcome outcome) {
        sources.computeIfAbsent(sourceId, k -> new AttemptHistory()).record(timestamp, outcome);
    }

    public AttemptHistory getHistory(String sourceId) {
        return sources.computeIfAbsent(sourceId, k -> new AttemptHistory());
    }

    public void clear() {
        sources.clear();
    }

    public java.util.Set<String> sourceIds() { return java.util.Set.copyOf(sources.keySet()); }

    public static final class AttemptHistory {
        private final Deque<Attempt> attempts = new LinkedBlockingDeque<>();
        private volatile int consecutiveFailures = 0;

        void record(Instant timestamp, AttemptOutcome outcome) {
            attempts.addLast(new Attempt(timestamp, outcome));
            while (attempts.size() > 10_000) attempts.pollFirst();
            switch (outcome) {
                case SUCCESS -> consecutiveFailures = 0;
                case FAILURE -> consecutiveFailures++;
                case BLOCKED -> { }
            }
        }

        public int countWithinWindow(long windowMs, Instant now) {
            prune(windowMs, now);
            return attempts.size();
        }

        public int failuresWithinWindow(long windowMs, Instant now) {
            prune(windowMs, now);
            return (int) attempts.stream().filter(a -> a.outcome() == AttemptOutcome.FAILURE).count();
        }

        public int getConsecutiveFailures() {
            return consecutiveFailures;
        }

        void resetStreak() {
            consecutiveFailures = 0;
        }

        public Instant getLastActivity() {
            Attempt last = attempts.peekLast();
            return last != null ? last.timestamp() : null;
        }

        public Instant getFirstActivity() {
            Attempt first = attempts.peekFirst();
            return first != null ? first.timestamp() : null;
        }

        private void prune(long windowMs, Instant now) {
            Instant cutoff = now.minusMillis(windowMs);
            Iterator<Attempt> it = attempts.iterator();
            while (it.hasNext()) {
                if (it.next().timestamp().isBefore(cutoff)) {
                    it.remove();
                } else {
                    break;
                }
            }
        }
    }

    public record Attempt(Instant timestamp, AttemptOutcome outcome) { }
}
