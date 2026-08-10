package com.anvex.attack;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public final class AttackRunner {

    private final LoginAttackScenario scenario;
    private final AttackClientSimulator client;
    private final int workerCount;

    public AttackRunner(
            LoginAttackScenario scenario,
            AttackClientSimulator client,
            int workerCount
    ) {
        if (scenario == null) {
            throw new IllegalArgumentException("Scenario cannot be null");
        }

        if (client == null) {
            throw new IllegalArgumentException("Client cannot be null");
        }

        if (workerCount <= 0) {
            throw new IllegalArgumentException("Worker count must be positive");
        }

        this.scenario = scenario;
        this.client = client;
        this.workerCount = workerCount;
    }

    public Result run() throws InterruptedException {
        AtomicInteger cursor = new AtomicInteger(0);
        AtomicInteger successfulAttempts = new AtomicInteger(0);
        AtomicInteger failedAttempts = new AtomicInteger(0);
        AtomicInteger errors = new AtomicInteger(0);

        CountDownLatch finished = new CountDownLatch(workerCount);

        for (int i = 0; i < workerCount; i++) {
            Thread worker = new Thread(() -> {
                try {
                    while (true) {
                        int index = claimNextIndex(cursor);

                        if (index == -1) {
                            break;
                        }

                        String username = scenario.getTargetUsername();
                        String password = scenario.getPassword(index);

                        try {
                            String response =
                                    client.attempt(username, password);

                            if ("SUCCESS".equals(response)) {
                                successfulAttempts.incrementAndGet();
                            } else {
                                failedAttempts.incrementAndGet();
                            }

                        } catch (IOException e) {
                            errors.incrementAndGet();
                        }
                    }
                } finally {
                    finished.countDown();
                }
            }, "attack-worker-" + i);

            worker.start();
        }

        finished.await();

        return new Result(
                scenario.getAttemptCount(),
                successfulAttempts.get(),
                failedAttempts.get(),
                errors.get(),
                cursor.get()
        );
    }

    private int claimNextIndex(AtomicInteger cursor) {
        while (true) {
            int current = cursor.get();

            if (current >= scenario.getAttemptCount()) {
                return -1;
            }

            if (cursor.compareAndSet(current, current + 1)) {
                return current;
            }
        }
    }

    public static final class Result {

        private final int totalAttempts;
        private final int successfulAttempts;
        private final int failedAttempts;
        private final int errors;
        private final int cursorValue;

        public Result(
                int totalAttempts,
                int successfulAttempts,
                int failedAttempts,
                int errors,
                int cursorValue
        ) {
            this.totalAttempts = totalAttempts;
            this.successfulAttempts = successfulAttempts;
            this.failedAttempts = failedAttempts;
            this.errors = errors;
            this.cursorValue = cursorValue;
        }

        public int getTotalAttempts() {
            return totalAttempts;
        }

        public int getSuccessfulAttempts() {
            return successfulAttempts;
        }

        public int getFailedAttempts() {
            return failedAttempts;
        }

        public int getErrors() {
            return errors;
        }

        public int getCursorValue() {
            return cursorValue;
        }
    }
}
