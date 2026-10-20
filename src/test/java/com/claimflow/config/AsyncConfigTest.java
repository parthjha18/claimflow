package com.claimflow.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncConfigTest {

    /**
     * Regression test for the pool deadlock found under load: when the notification queue is full, the task must
     * be dropped, never executed on the submitting (request) thread.
     */
    @Test
    void fullQueueDropsNotificationsInsteadOfRunningThemOnTheCallerThread() throws Exception {
        ThreadPoolTaskExecutor executor = AsyncConfig.newNotificationExecutor(1, 1, 1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        try {
            executor.execute(() -> {          // occupies the only worker
                started.countDown();
                await(release);
            });
            started.await();
            executor.execute(() -> await(release));   // fills the single queue slot

            AtomicReference<Thread> ranOn = new AtomicReference<>();
            executor.execute(() -> ranOn.set(Thread.currentThread()));   // rejected

            assertThat(ranOn.get()).as("rejected task must not run on the caller").isNull();
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
