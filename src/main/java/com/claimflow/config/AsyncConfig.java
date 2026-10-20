package com.claimflow.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean("notificationExecutor")
    Executor notificationExecutor() {
        return newNotificationExecutor(2, 4, 2000);
    }

    /**
     * Notifications are best-effort, so when the queue is full the newest one is dropped with a warning.
     *
     * Do NOT use CallerRunsPolicy here. The listener runs after commit, on the request thread that still holds
     * its transaction's database connection, and itself needs a second connection. Under load every request
     * thread then waits for a connection that only another waiting thread can release: the pool deadlocks until
     * the connection timeout (found by the k6 load test, which stalled for 30 s).
     */
    static ThreadPoolTaskExecutor newNotificationExecutor(int core, int max, int queue) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(core);
        ex.setMaxPoolSize(max);
        ex.setQueueCapacity(queue);
        ex.setThreadNamePrefix("notify-");
        ex.setRejectedExecutionHandler((task, pool) ->
                log.warn("Notification queue full ({} waiting); dropping one notification", pool.getQueue().size()));
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(10);
        ex.initialize();
        return ex;
    }
}
